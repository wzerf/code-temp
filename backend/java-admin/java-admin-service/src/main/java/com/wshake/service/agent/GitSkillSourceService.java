package com.wshake.service.agent;

import static com.wshake.service.agent.GitSkillSourceModels.*;

import com.wshake.common.constant.StatusFlags;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.common.time.TimeZones;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftCommand;
import com.wshake.service.agent.SkillControlModels.UpdateSkillDraftCommand;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.entity.AgentSkillGitSync;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 受控 Git 导入：只把经解析的包写入 Skill 草稿，永不直接发布。 */
@Service
@RequiredArgsConstructor
public class GitSkillSourceService {

    private static final String SCOPE_MARKET = "MARKET";
    private static final String SCOPE_PRIVATE = "PRIVATE";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_SKILLS = 100;
    private static final int MAX_FILES_PER_SKILL = 200;
    private static final long MAX_BYTES_PER_SKILL = 4L * 1024 * 1024;

    private final AgentSkillGitSourceRepository sourceRepository;
    private final AgentSkillGitSyncRepository syncRepository;
    private final AgentSkillDraftRepository draftRepository;
    private final SkillControlService skillControlService;
    private final GitSkillSourceProperties properties;

    @Transactional
    public SourceView create(CreateCommand command) {
        String scope = requireScope(command.scope());
        requireScopeAccess(command.userId(), command.administrator(), scope);
        AgentSkillGitSource source = new AgentSkillGitSource();
        source.setScope(scope);
        source.setOwnerUserId(SCOPE_MARKET.equals(scope) ? 0L : requireUserId(command.userId()));
        source.setUrl(requireSafeHttpsUrl(command.url()));
        source.setRef(normalizeRef(command.ref()));
        source.setSubdirectory(normalizeSubdirectory(command.subdirectory()));
        source.setSecretRef(normalizeSecretRef(command.secretRef()));
        source.setStatus(STATUS_READY);
        source.setLastError("");
        source.setIsEnabled(StatusFlags.ENABLED);
        sourceRepository.insert(source);
        return toView(source);
    }

    public List<SourceView> list(Long userId, boolean administrator) {
        Long currentUserId = requireUserId(userId);
        List<AgentSkillGitSource> rows = new ArrayList<>();
        if (administrator) {
            rows.addAll(sourceRepository.listMarket());
        }
        rows.addAll(sourceRepository.listPrivateByOwner(currentUserId));
        return rows.stream().map(this::toView).toList();
    }

    public SourceView get(Long id, Long userId, boolean administrator) {
        return toView(requireAccessible(id, userId, administrator));
    }

    @Transactional
    public SourceView update(UpdateCommand command) {
        AgentSkillGitSource source = requireAccessible(command.id(), command.userId(), command.administrator());
        if (command.urlPresent()) {
            source.setUrl(requireSafeHttpsUrl(command.url()));
        }
        if (command.refPresent()) {
            source.setRef(normalizeRef(command.ref()));
        }
        if (command.subdirectoryPresent()) {
            source.setSubdirectory(normalizeSubdirectory(command.subdirectory()));
        }
        if (command.secretRefPresent()) {
            source.setSecretRef(normalizeSecretRef(command.secretRef()));
        }
        source.setLastCommitSha(null);
        source.setLastSyncedAt(null);
        source.setStatus(STATUS_READY);
        source.setLastError("");
        sourceRepository.update(source);
        return toView(source);
    }

    @Transactional
    public void delete(Long id, Long userId, boolean administrator) {
        sourceRepository.delete(requireAccessible(id, userId, administrator));
    }

    public PreviewView preview(Long id, Long userId, boolean administrator) {
        AgentSkillGitSource source = requireAccessible(id, userId, administrator);
        try (Checkout checkout = checkout(source)) {
            List<ImportedSkill> skills = discoverSkills(checkout.root(), source.getSubdirectory());
            return new PreviewView(source.getId(), checkout.commitSha(), skills.stream().map(ImportedSkill::toPreview).toList());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            markFailed(source, "git source preview failed");
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "git source preview failed");
        }
    }

    @Transactional
    public SyncView sync(Long id, String expectedCommitSha, List<String> skillPaths, Long userId, boolean administrator) {
        AgentSkillGitSource source = requireAccessible(id, userId, administrator);
        if (expectedCommitSha == null || expectedCommitSha.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "expectedCommitSha is required");
        }
        try (Checkout checkout = checkout(source)) {
            if (!expectedCommitSha.equals(checkout.commitSha())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "git source HEAD changed; preview again before syncing");
            }
            Map<String, ImportedSkill> available = new LinkedHashMap<>();
            for (ImportedSkill skill : discoverSkills(checkout.root(), source.getSubdirectory())) {
                available.put(skill.path(), skill);
            }
            List<SyncItem> results = new ArrayList<>();
            for (String path : skillPaths == null ? List.<String>of() : skillPaths.stream().distinct().toList()) {
                ImportedSkill skill = available.get(path);
                if (skill == null) {
                    results.add(new SyncItem(path, null, "FAILED", null, "skill path was not found in preview"));
                    continue;
                }
                results.add(syncOne(source, checkout.commitSha(), skill, userId));
            }
            source.setLastCommitSha(checkout.commitSha());
            source.setLastSyncedAt(TimeZones.now());
            source.setStatus(STATUS_READY);
            source.setLastError("");
            sourceRepository.update(source);
            return new SyncView(source.getId(), checkout.commitSha(), results);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            markFailed(source, "git source sync failed");
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "git source sync failed");
        }
    }

    private SyncItem syncOne(AgentSkillGitSource source, String commitSha, ImportedSkill skill, Long userId) {
        AgentSkillGitSync prior = syncRepository.findBySourceAndPath(source.getId(), skill.path());
        if (prior != null && skill.contentHash().equals(prior.getContentHash())) {
            return new SyncItem(skill.path(), skill.name(), "UNCHANGED", prior.getDraftId(), "content is unchanged");
        }
        try {
            Long draftId;
            String status;
            if (prior == null) {
                var draft = skillControlService.createDraft(new CreateSkillDraftCommand(
                        requireUserId(userId), skill.name(), skill.description(), skill.skillContent(), source.getScope(), skill.resources(), null, "Imported from Git"));
                draftId = draft.id();
                prior = new AgentSkillGitSync();
                prior.setSourceId(source.getId());
                prior.setSkillPath(skill.path());
                prior.setIsEnabled(StatusFlags.ENABLED);
                status = "CREATED";
            } else {
                AgentSkillDraft draft = draftRepository.findById(prior.getDraftId());
                if (draft == null || (!SkillControlModels.DRAFT.equals(draft.getStatus()) && !SkillControlModels.REJECTED.equals(draft.getStatus())) || !prior.getContentHash().equals(draft.getContentHash())) {
                    return new SyncItem(skill.path(), skill.name(), "CONFLICT", prior.getDraftId(), "git sync conflicts with an active draft: " + skill.name());
                }
                var updated = skillControlService.updateDraft(new UpdateSkillDraftCommand(
                        draft.getId(), requireUserId(userId), skill.description(), true, skill.skillContent(), true, skill.resources(), true, draft.getRemark(), false));
                draftId = updated.id();
                status = "UPDATED";
            }
            prior.setCommitSha(commitSha);
            prior.setContentHash(skill.contentHash());
            prior.setDraftId(draftId);
            if (prior.getId() == null) {
                syncRepository.insert(prior);
            } else {
                syncRepository.update(prior);
            }
            return new SyncItem(skill.path(), skill.name(), status, draftId, "");
        } catch (BizException exception) {
            return new SyncItem(skill.path(), skill.name(), "CONFLICT", prior == null ? null : prior.getDraftId(), exception.getMessage());
        }
    }

    private Checkout checkout(AgentSkillGitSource source) throws Exception {
        Path directory = Files.createTempDirectory("agent-skill-git-");
        try {
            var clone = Git.cloneRepository()
                    .setURI(source.getUrl())
                    .setDirectory(directory.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .setTimeout(30);
            if (!"HEAD".equals(source.getRef())) {
                clone.setBranch(source.getRef());
            }
            if (!source.getSecretRef().isBlank()) {
                GitSkillSourceProperties.Credential credential = properties.getCredentials().get(source.getSecretRef());
                if (credential == null || credential.getUsername().isBlank() || credential.getPassword().isBlank()) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "git source credential reference is not configured");
                }
                clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(
                        credential.getUsername(), credential.getPassword()));
            }
            Git git = clone.call();
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "git ref not found");
            }
            return new Checkout(directory, git, head.name());
        } catch (Exception exception) {
            deleteTree(directory);
            if (exception instanceof BizException business) {
                throw business;
            }
            throw BizException.of(ResultCode.PARAM_INVALID, "git ref not found");
        }
    }

    private static List<ImportedSkill> discoverSkills(Path root, String subdirectory) throws IOException {
        Path base = root.resolve(subdirectory).normalize();
        if (!base.startsWith(root) || !Files.isDirectory(base)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git subdirectory is invalid");
        }
        List<Path> manifests;
        try (Stream<Path> files = Files.find(base, 4, (path, attrs) -> attrs.isRegularFile() && "SKILL.md".equals(path.getFileName().toString()))) {
            manifests = files.sorted().limit(MAX_SKILLS + 1L).toList();
        }
        if (manifests.size() > MAX_SKILLS) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source exceeds configured limit");
        }
        List<ImportedSkill> skills = new ArrayList<>();
        for (Path manifest : manifests) {
            try {
                Path packageRoot = manifest.getParent();
                Map<String, String> resources = new LinkedHashMap<>();
                long[] bytes = {Files.size(manifest)};
                if (bytes[0] > MAX_BYTES_PER_SKILL) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "git source exceeds configured limit");
                }
                try (Stream<Path> files = Files.walk(packageRoot)) {
                    List<Path> resourceFiles = files.filter(Files::isRegularFile).sorted().toList();
                    if (resourceFiles.size() > MAX_FILES_PER_SKILL) {
                        throw BizException.of(ResultCode.PARAM_INVALID, "git source exceeds configured limit");
                    }
                    for (Path resource : resourceFiles) {
                        if (resource.equals(manifest)) continue;
                        String path = packageRoot.relativize(resource).toString().replace('\\', '/');
                        AgentSkillMarkdown.validateResourcePath(path);
                        byte[] content = Files.readAllBytes(resource);
                        bytes[0] += content.length;
                        if (bytes[0] > MAX_BYTES_PER_SKILL) {
                            throw BizException.of(ResultCode.PARAM_INVALID, "git source exceeds configured limit");
                        }
                        resources.put(path, new String(content, StandardCharsets.UTF_8));
                    }
                }
                String skillContent = Files.readString(manifest, StandardCharsets.UTF_8);
                AgentSkillMarkdown.Frontmatter frontmatter = AgentSkillMarkdown.parse(skillContent);
                String path = base.relativize(packageRoot).toString().replace('\\', '/');
                skills.add(new ImportedSkill(
                        path,
                        frontmatter.name(),
                        frontmatter.description(),
                        skillContent,
                        resources,
                        AgentSkillContentHash.sha256(skillContent, resources),
                        bytes[0]));
            } catch (BizException | IOException ignored) {
                // 单个包无法导入时跳过；同步仍会为该请求路径返回 FAILED，其余合法包继续。
            }
        }
        return skills;
    }

    private AgentSkillGitSource requireAccessible(Long id, Long userId, boolean administrator) {
        if (id == null) throw BizException.of(ResultCode.PARAM_INVALID, "git source id is required");
        AgentSkillGitSource source = sourceRepository.findById(id);
        if (source == null) throw BizException.of(ResultCode.PARAM_INVALID, "git source not found");
        if (SCOPE_MARKET.equals(source.getScope())) {
            if (!administrator) throw BizException.of(ResultCode.AUTH_FORBIDDEN, "market git source requires administrator");
        } else if (!source.getOwnerUserId().equals(requireUserId(userId))) {
            throw BizException.of(ResultCode.AUTH_FORBIDDEN, "git source is not owned by current user");
        }
        return source;
    }

    private static void requireScopeAccess(Long userId, boolean administrator, String scope) {
        requireUserId(userId);
        if (SCOPE_MARKET.equals(scope) && !administrator) {
            throw BizException.of(ResultCode.AUTH_FORBIDDEN, "market git source requires administrator");
        }
    }

    private static String requireScope(String scope) {
        if (SCOPE_MARKET.equals(scope) || SCOPE_PRIVATE.equals(scope)) return scope;
        throw BizException.of(ResultCode.PARAM_INVALID, "scope must be MARKET or PRIVATE");
    }

    private static String requireSafeHttpsUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "git source URL must use HTTPS");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "git source target is not allowed");
                }
            }
            return uri.toASCIIString();
        } catch (UnknownHostException exception) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source target is not allowed");
        } catch (IllegalArgumentException exception) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source URL must use HTTPS");
        }
    }

    private static String normalizeRef(String value) { return value == null || value.isBlank() ? "HEAD" : value.trim(); }
    private static String normalizeSecretRef(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeSubdirectory(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("//")) throw BizException.of(ResultCode.PARAM_INVALID, "git subdirectory is invalid");
        return normalized;
    }
    private static Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) throw BizException.of(ResultCode.AUTH_NOT_LOGIN, "owner user is required");
        return userId;
    }
    private void markFailed(AgentSkillGitSource source, String message) {
        source.setStatus(STATUS_FAILED);
        source.setLastError(message);
        sourceRepository.update(source);
    }
    private SourceView toView(AgentSkillGitSource source) {
        return new SourceView(source.getId(), source.getScope(), source.getOwnerUserId(), source.getUrl(), source.getRef(), source.getSubdirectory(), !source.getSecretRef().isBlank(), source.getLastCommitSha(), source.getLastSyncedAt(), source.getStatus(), source.getLastError(), source.getCreatedAt(), source.getUpdatedAt());
    }
    private static void deleteTree(Path root) {
        if (root == null) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不应掩盖同步结果，操作系统会在进程结束后回收。
                }
            });
        } catch (IOException ignored) {
            // 目录已被并发清理时无需处理。
        }
    }
    private record ImportedSkill(String path, String name, String description, String skillContent, Map<String, String> resources, String contentHash, long totalBytes) {
        PreviewItem toPreview() { return new PreviewItem(path, name, description, contentHash, resources.size(), totalBytes); }
    }
    private static final class Checkout implements AutoCloseable {
        private final Path root; private final Git git; private final String commitSha;
        private Checkout(Path root, Git git, String commitSha) { this.root = root; this.git = git; this.commitSha = commitSha; }
        Path root() { return root; } String commitSha() { return commitSha; }
        @Override public void close() { git.close(); deleteTree(root); }
    }
}
