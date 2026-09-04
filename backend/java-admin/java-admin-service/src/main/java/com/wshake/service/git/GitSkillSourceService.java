package com.wshake.service.git;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.common.time.TimeZones;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.entity.AgentSkillGitSync;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillDraftResourceRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import com.wshake.service.skill.SkillContentHasher;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Git Skill 受控导入（docs/agent-module-architecture.md §6.5）。
 *
 * <p>Git 不是运行时授权来源：控制面把 Git 包解析为 Skill 草稿,发布后仍由
 * 不可变 Release 与 Revision Binding 装配运行。安全红线：
 * <ul>
 *     <li>仅 HTTPS（禁 SSH/本地路径/URL user-info）;目标主机禁回环/私网/链路本地/保留地址</li>
 *     <li>clone/fetch 有超时;包文件数/大小受类型化限制(常量)</li>
 *     <li>密钥只存 {@code encrypted_secret} 密文,clone 凭据仅内存</li>
 *     <li>{@code preview} 不写草稿;{@code sync} 需 expectedCommitSha = 服务器重新解析的 HEAD</li>
 * </ul>
 *
 * @author wshake
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitSkillSourceService {

    /** 单包最大文件数(含 SKILL.md)。 */
    static final int MAX_FILES_PER_SKILL = 50;
    /** 单个文件最大字节(约 512 KB;资源以文本为主)。 */
    static final int MAX_BYTES_PER_FILE = 512 * 1024;
    /** 扫描的最大包数。 */
    static final int MAX_SKILLS_PER_SCAN = 200;
    /** JGit 网络超时毫秒。 */
    static final int GIT_TIMEOUT_MS = 30_000;

    private final AgentSkillGitSourceRepository sourceRepository;
    private final AgentSkillGitSyncRepository syncRepository;
    private final AgentSkillDraftRepository draftRepository;
    private final AgentSkillDraftResourceRepository draftResourceRepository;
    private final AgentSecretCipher secretCipher;

    // ---------- 来源 CRUD ----------

    public List<GitSourceView> listSources(String scope, Long ownerUserId) {
        List<AgentSkillGitSource> rows = sourceRepository.listByScope(normalizeScope(scope), ownerUserId);
        List<GitSourceView> views = new ArrayList<>();
        for (AgentSkillGitSource row : rows) {
            views.add(toSourceView(row));
        }
        return views;
    }

    public GitSourceView getSource(Long id) {
        return toSourceView(requireSource(id));
    }

    @Transactional
    public GitSourceView createSource(CreateGitSourceCommand cmd) {
        String scope = requireScope(cmd.scope());
        Long owner = cmd.ownerUserId() == null || cmd.ownerUserId() <= 0 ? 0L : cmd.ownerUserId();
        String url = requireHttpsUrl(cmd.url());

        AgentSkillGitSource row = new AgentSkillGitSource();
        row.setScope(scope);
        row.setOwnerUserId(owner);
        row.setUrl(url);
        row.setRef(cmd.ref() == null || cmd.ref().isBlank() ? "HEAD" : cmd.ref().trim());
        row.setSubdirectory(normalizeSubdir(cmd.subdirectory()));
        row.setEncryptedSecret(secretCipher.encrypt(cmd.plainSecret()));
        row.setStatus("READY");
        row.setRemark(cmd.remark() == null ? "" : cmd.remark().trim());
        row.setIsEnabled(1);
        sourceRepository.insert(row);
        return toSourceView(requireSource(row.getId()));
    }

    @Transactional
    public GitSourceView updateSource(Long id, UpdateGitSourceCommand cmd) {
        AgentSkillGitSource row = requireSource(id);
        if (cmd.ref() != null) {
            row.setRef(cmd.ref().isBlank() ? "HEAD" : cmd.ref().trim());
        }
        if (cmd.subdirectory() != null) {
            row.setSubdirectory(normalizeSubdir(cmd.subdirectory()));
        }
        if (cmd.plainSecret() != null) {
            row.setEncryptedSecret(secretCipher.encrypt(cmd.plainSecret()));
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        sourceRepository.update(row);
        return toSourceView(requireSource(id));
    }

    @Transactional
    public void deleteSource(Long id) {
        requireSource(id);
        sourceRepository.softDeleteById(id);
    }

    // ---------- preview / sync ----------

    /**
     * preview：解析 ref 为精确 commit_sha 并扫描包（不写草稿）。
     */
    public GitPreviewResult preview(Long sourceId) {
        AgentSkillGitSource source = requireSource(sourceId);
        try {
            Path workDir = Files.createTempDirectory("agentskill-preview-");
            try {
                Git git = clone(source, workDir);
                try {
                    ObjectId head = resolveRef(git, source.getRef());
                    String commitSha = head.name();
                    String subdir = source.getSubdirectory();
                    List<SkillPackageScan> packages = scanSkillPackages(workDir, subdir);
                    return new GitPreviewResult(sourceId, source.getRef(), commitSha, source.getUrl(), packages);
                } finally {
                    git.close();
                }
            } finally {
                deleteRecursively(workDir);
            }
        } catch (GitAPIException | IOException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "Git preview 失败: " + rootMessage(e));
        }
    }

    /**
     * sync：expectedCommitSha 必须等于服务器重新解析的 HEAD,逐包幂等同步。
     */
    @Transactional
    public GitSyncResult sync(Long sourceId, String expectedCommitSha) {
        AgentSkillGitSource source = requireSource(sourceId);
        try {
            Path workDir = Files.createTempDirectory("agentskill-sync-");
            try {
                Git git = clone(source, workDir);
                try {
                    ObjectId head = resolveRef(git, source.getRef());
                    String commitSha = head.name();
                    if (expectedCommitSha == null || !expectedCommitSha.equals(commitSha)) {
                        markSourceFailed(source, "expectedCommitSha 与服务器解析 HEAD 不一致(" + commitSha + ")");
                        return new GitSyncResult(
                                sourceId,
                                commitSha,
                                List.of(new GitSyncItem(
                                        "", "FAILED", "expectedCommitSha mismatch: server=" + commitSha)));
                    }

                    String subdir = source.getSubdirectory();
                    List<SkillPackageScan> packages = scanSkillPackages(workDir, subdir);
                    List<GitSyncItem> items = new ArrayList<>();
                    for (SkillPackageScan pkg : packages) {
                        items.add(syncOnePackage(source, git, commitSha, pkg));
                    }
                    if (items.isEmpty()) {
                        markSourceFailed(source, "未在 subdirectory 下发现含 SKILL.md 的包");
                    } else {
                        markSourceReady(source, commitSha);
                    }
                    return new GitSyncResult(sourceId, commitSha, items);
                } finally {
                    git.close();
                }
            } finally {
                deleteRecursively(workDir);
            }
        } catch (GitAPIException | IOException e) {
            markSourceFailed(source, rootMessage(e));
            throw new BizException(ResultCode.PARAM_INVALID, "Git sync 失败: " + rootMessage(e));
        }
    }

    /** 单包同步判定：同 commit 同 hash → UNCHANGED;可安全更新 → UPDATED;新包 → CREATED;活跃草稿冲突 → CONFLICT。 */
    private GitSyncItem syncOnePackage(AgentSkillGitSource source, Git git, String commitSha, SkillPackageScan pkg) {
        try {
            AgentSkillGitSync existing = syncRepository.findBySourceAndPath(source.getId(), pkg.skillPath());
            if (existing != null
                    && commitSha.equals(existing.getCommitSha())
                    && pkg.contentHash().equals(existing.getContentHash())) {
                return new GitSyncItem(pkg.skillPath(), "UNCHANGED", "");
            }

            Long owner = source.getOwnerUserId();
            String visibility = "MARKET".equals(source.getScope()) ? "MARKET" : "PRIVATE";
            AgentSkillDraft draft = null;
            if (draftRepository.existsActiveDraft(owner, pkg.name(), visibility, null)) {
                // 有活跃草稿(非 CONSUMED)且非本来源上一次同步的行 → 冲突,保护人工修改
                boolean isSameDraft = existing != null
                        && existing.getDraftId() != null
                        && draftRepository.findById(existing.getDraftId()) != null;
                if (!isSameDraft) {
                    // 兼容 sync 记录缺失的历史:活跃草稿 remark 标注为本来源同步创建 → 视为同源,可接管更新
                    AgentSkillDraft active =
                            draftRepository.listActiveByOwnerAndName(owner, pkg.name(), visibility).stream()
                                    .filter(d -> "DRAFT".equals(d.getStatus()) || "REJECTED".equals(d.getStatus()))
                                    .findFirst()
                                    .orElse(null);
                    boolean ownedByThisSource = active != null
                            && active.getRemark() != null
                            && active.getRemark().startsWith("git source #" + source.getId() + " ");
                    if (!ownedByThisSource) {
                        return new GitSyncItem(pkg.skillPath(), "CONFLICT", "同名活跃草稿已存在,拒绝覆盖");
                    }
                    draft = active;
                }
            }

            // upsert 草稿
            boolean draftConsumed = false;
            if (existing != null && existing.getDraftId() != null) {
                draft = draftRepository.findById(existing.getDraftId());
                draftConsumed = draft != null && "CONSUMED".equals(draft.getStatus());
            }
            if (draftConsumed) {
                // 原草稿已消费(已发布)→ 开新草稿,不触碰历史
                draft = null;
            }
            String result;
            if (existing == null) {
                result = "CREATED";
            } else {
                result = draft == null ? "CREATED" : "UPDATED";
            }
            if (draft == null) {
                draft = new AgentSkillDraft();
            }
            draft.setOwnerUserId(owner);
            draft.setName(pkg.name());
            draft.setVisibility(visibility);
            draft.setStatus("DRAFT");
            draft.setDescription(clip(pkg.description(), 512));
            draft.setSkillContent(pkg.skillContent());
            draft.setContentHash(pkg.contentHash());
            draft.setRemark("git source #" + source.getId() + " " + commitSha);
            draft.setIsEnabled(1);
            if (draft.getId() == null) {
                draftRepository.insert(draft);
            } else {
                draftRepository.update(draft);
            }
            // 资源全量替换
            draftResourceRepository.deleteByDraftId(draft.getId());
            if (!pkg.resources().isEmpty()) {
                List<AgentSkillDraftResource> rows = new ArrayList<>();
                for (SkillResourceData r : pkg.resources()) {
                    AgentSkillDraftResource res = new AgentSkillDraftResource();
                    res.setDraftId(draft.getId());
                    res.setResourcePath(r.resourcePath());
                    res.setContent(r.content());
                    res.setContentHash("");
                    rows.add(res);
                }
                draftResourceRepository.insertAll(rows);
            }

            // upsert 同步记录
            if (existing == null) {
                AgentSkillGitSync syncRow = new AgentSkillGitSync();
                syncRow.setSourceId(source.getId());
                syncRow.setCommitSha(commitSha);
                syncRow.setSkillPath(pkg.skillPath());
                syncRow.setContentHash(pkg.contentHash());
                syncRow.setDraftId(draft.getId());
                syncRow.setResult(result);
                syncRow.setDeletedAt(0L);
                syncRow.setCreatedAt(java.time.LocalDateTime.now());
                syncRow.setUpdatedAt(java.time.LocalDateTime.now());
                syncRepository.insert(syncRow);
            } else {
                existing.setCommitSha(commitSha);
                existing.setContentHash(pkg.contentHash());
                existing.setDraftId(draft.getId());
                existing.setResult(result);
                existing.setDeletedAt(0L);
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                syncRepository.update(existing);
            }
            return new GitSyncItem(pkg.skillPath(), result, "");
        } catch (BizException e) {
            log.warn("syncOnePackage failed [{}]: {}", pkg.skillPath(), e.getMessage());
            return new GitSyncItem(pkg.skillPath(), "FAILED", e.getMessage());
        } catch (Exception e) {
            log.warn("syncOnePackage failed [{}]", pkg.skillPath(), e);
            return new GitSyncItem(pkg.skillPath(), "FAILED", rootMessage(e));
        }
    }

    // ---------- JGit 内部 ----------

    private Git clone(AgentSkillGitSource source, Path workDir) throws GitAPIException {
        // 凭据仅在内存;解密失败(如无密钥)则匿名
        String secret = secretCipher.decrypt(source.getEncryptedSecret());
        var cloneCmd = Git.cloneRepository()
                .setURI(source.getUrl())
                .setDirectory(workDir.toFile())
                .setTimeout(GIT_TIMEOUT_MS / 1000);
        if (secret != null && !secret.isEmpty()) {
            cloneCmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", secret));
        }
        return cloneCmd.call();
    }

    private static ObjectId resolveRef(Git git, String ref) throws IOException {
        Repository repo = git.getRepository();
        ObjectId head = repo.resolve(ref);
        if (head == null) {
            throw new IOException("无法解析 ref: " + ref);
        }
        return head;
    }

    /** 递归扫描 subdirectory 下的 SKILL 包:含 SKILL.md 的目录。 */
    private List<SkillPackageScan> scanSkillPackages(Path workDir, String subdir) throws IOException {
        Path root = workDir;
        if (subdir != null && !subdir.isEmpty()) {
            Path resolved = root.resolve(subdir).normalize();
            if (!resolved.startsWith(root)) {
                throw new IOException("subdirectory 越界");
            }
            root = resolved;
        }
        if (!Files.isDirectory(root)) {
            throw new IOException("subdirectory 不存在: " + subdir);
        }
        List<SkillPackageScan> packages = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            List<Path> skillMdFiles = walk.filter(
                            p -> p.getFileName().toString().equals("SKILL.md"))
                    .toList();
            for (Path skillMd : skillMdFiles) {
                if (packages.size() >= MAX_SKILLS_PER_SCAN) {
                    break;
                }
                Path pkgDir = skillMd.getParent();
                if (pkgDir == null) {
                    continue;
                }
                packages.add(scanOnePackage(root, pkgDir));
            }
        }
        packages.sort(Comparator.comparing(SkillPackageScan::skillPath));
        return packages;
    }

    private SkillPackageScan scanOnePackage(Path root, Path pkgDir) throws IOException {
        String skillContent = Files.readString(pkgDir.resolve("SKILL.md"), StandardCharsets.UTF_8);
        SkillNameDescription nd = parseFrontmatter(skillContent);

        List<Path> files;
        try (var walk = Files.walk(pkgDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .toList();
        }
        if (files.size() > MAX_FILES_PER_SKILL) {
            throw new IOException("包文件数超限(" + files.size() + ">" + MAX_FILES_PER_SKILL + ")");
        }
        List<SkillResourceData> resources = new ArrayList<>();
        List<SkillContentHasher.ResourceEntry> entries = new ArrayList<>();
        for (Path file : files) {
            long size = Files.size(file);
            if (size > MAX_BYTES_PER_FILE) {
                throw new IOException("文件超限: " + file.getFileName() + " (" + size + " bytes)");
            }
            String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
            String content = Files.readString(file, StandardCharsets.UTF_8);
            resources.add(new SkillResourceData(relative, content));
            entries.add(new SkillContentHasher.ResourceEntry(relative, content));
        }
        String hash = SkillContentHasher.hash(skillContent, entries);
        // skillPath = pkgDir 相对 root
        String skillPath = root.relativize(pkgDir).toString().replace(File.separatorChar, '/');
        if (skillPath.isEmpty()) {
            skillPath = ".";
        }
        return new SkillPackageScan(skillPath, nd.name(), nd.description(), skillContent, resources, hash);
    }

    /** 解析 SKILL.md frontmatter 的 name/description(YAML 极小子集)。 */
    private static SkillNameDescription parseFrontmatter(String content) {
        String name = "";
        String description = "";
        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end > 0) {
                String fm = content.substring(3, end);
                for (String line : fm.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("name:") && name.isEmpty()) {
                        name = trimmed.substring("name:".length()).trim().replaceAll("[\"']", "");
                    } else if (trimmed.startsWith("description:") && description.isEmpty()) {
                        description = trimmed.substring("description:".length())
                                .trim()
                                .replaceAll("[\"']", "");
                    }
                }
            }
        }
        if (name.isEmpty()) {
            name = "untitled-skill";
        }
        return new SkillNameDescription(name, description);
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // 忽略清理失败
                }
            });
        } catch (IOException e) {
            // 忽略
        }
    }

    private void markSourceReady(AgentSkillGitSource source, String commitSha) {
        source.setLastCommitSha(commitSha);
        source.setLastSyncedAt(TimeZones.now());
        source.setStatus("READY");
        source.setLastError("");
        sourceRepository.update(source);
    }

    private void markSourceFailed(AgentSkillGitSource source, String error) {
        source.setStatus("FAILED");
        source.setLastError(clip(error, 512));
        sourceRepository.update(source);
    }

    // ---------- 安全校验 ----------

    private static String requireHttpsUrl(String raw) {
        if (raw == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "url 不能为空");
        }
        String url = raw.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            // SCP 语法(git@host:path)或畸形地址一律拒绝
            throw new BizException(ResultCode.PARAM_INVALID, "仅支持 HTTPS Git 地址");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BizException(ResultCode.PARAM_INVALID, "仅支持 HTTPS Git 地址");
        }
        if (uri.getUserInfo() != null) {
            throw new BizException(ResultCode.PARAM_INVALID, "url 不得包含 user-info;请通过密钥配置");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "url 缺少主机");
        }
        rejectUnsafeHost(host);
        return url;
    }

    /** 拒绝回环/私网/链路本地/保留地址(含 hostname 解析后的所有地址)。 */
    private static void rejectUnsafeHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "无法解析 Git 主机: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()) {
                throw new BizException(ResultCode.PARAM_INVALID, "Git 地址指向内网/保留地址,已拒绝");
            }
        }
    }

    private static String normalizeSubdir(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String sub = raw.trim().replace('\\', '/');
        while (sub.startsWith("/")) {
            sub = sub.substring(1);
        }
        while (sub.endsWith("/")) {
            sub = sub.substring(0, sub.length() - 1);
        }
        if (sub.contains("..")) {
            throw new BizException(ResultCode.PARAM_INVALID, "subdirectory 不得包含 ..");
        }
        return sub;
    }

    private static String requireScope(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"MARKET".equals(s) && !"PRIVATE".equals(s)) {
            throw new BizException(ResultCode.PARAM_INVALID, "scope must be MARKET|PRIVATE");
        }
        return s;
    }

    private static String normalizeScope(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private AgentSkillGitSource requireSource(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentSkillGitSource row = sourceRepository.findById(id);
        if (row == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "git source " + id + " not found");
        }
        return row;
    }

    private GitSourceView toSourceView(AgentSkillGitSource row) {
        return new GitSourceView(
                row.getId(),
                row.getScope(),
                row.getOwnerUserId(),
                row.getUrl(),
                row.getRef(),
                row.getSubdirectory(),
                row.getLastCommitSha(),
                row.getLastSyncedAt(),
                row.getStatus(),
                row.getLastError(),
                row.getRemark(),
                row.getIsEnabled(),
                row.getDeletedAt() == null ? 0L : row.getDeletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy() == null ? 0L : row.getCreatedBy(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy());
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.getClass().getSimpleName() : msg;
    }

    // ---------- 领域模型 ----------

    public record CreateGitSourceCommand(
            String scope,
            Long ownerUserId,
            String url,
            String ref,
            String subdirectory,
            String plainSecret,
            String remark) {}

    public record UpdateGitSourceCommand(String ref, String subdirectory, String plainSecret, String remark) {}

    public record GitSourceView(
            Long id,
            String scope,
            Long ownerUserId,
            String url,
            String ref,
            String subdirectory,
            String lastCommitSha,
            java.time.LocalDateTime lastSyncedAt,
            String status,
            String lastError,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {}

    public record GitPreviewResult(
            Long sourceId, String ref, String commitSha, String url, List<SkillPackageScan> packages) {}

    /** 扫描到的 Skill 包。 */
    public record SkillPackageScan(
            String skillPath,
            String name,
            String description,
            String skillContent,
            List<SkillResourceData> resources,
            String contentHash) {}

    public record SkillResourceData(String resourcePath, String content) {}

    public record GitSyncResult(Long sourceId, String commitSha, List<GitSyncItem> items) {}

    public record GitSyncItem(String skillPath, String result, String message) {}

    private record SkillNameDescription(String name, String description) {}
}
