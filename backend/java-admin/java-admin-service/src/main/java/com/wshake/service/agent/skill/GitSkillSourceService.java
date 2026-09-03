package com.wshake.service.agent.skill;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.skill.SkillManageModels.GitPackagePreview;
import com.wshake.service.agent.skill.SkillManageModels.GitPreviewResult;
import com.wshake.service.agent.skill.SkillManageModels.GitSourceCommand;
import com.wshake.service.agent.skill.SkillManageModels.GitSyncCommand;
import com.wshake.service.agent.skill.SkillManageModels.GitSyncItem;
import com.wshake.service.agent.skill.SkillManageModels.GitSyncResult;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.entity.AgentSkillGitSync;
import com.wshake.service.port.GitSkillSourcePort;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillGitRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillResourceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Git 受控导入 Service：preview / sync。
 *
 * <p>同步语义：preview 解析 ref 为精确 commit；sync 要求 expectedCommitSha 等于服务器重新解析的 HEAD，
 * 逐包创建/更新草稿，按 content_hash 幂等。单个包失败不影响同次其他合法包。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class GitSkillSourceService {

    private final GitSkillSourcePort gitPort;
    private final AgentSkillGitRepository gitRepository;
    private final AgentSkillDraftRepository draftRepository;
    private final AgentSkillReleaseRepository releaseRepository;
    private final AgentSkillResourceRepository resourceRepository;

    public List<AgentSkillGitSource> listSources() {
        return gitRepository.listSources();
    }

    @Transactional
    public AgentSkillGitSource createSource(GitSourceCommand cmd) {
        String scope = requireScope(cmd.scope());
        String url = requireUrl(cmd.url());
        Long owner = cmd.ownerUserId() == null ? 0L : cmd.ownerUserId();
        if (gitRepository.existsSource(scope, owner, url, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source already exists");
        }
        AgentSkillGitSource row = new AgentSkillGitSource();
        row.setScope(scope);
        row.setOwnerUserId(owner);
        row.setUrl(url);
        row.setRef(cmd.ref() == null || cmd.ref().isBlank() ? "main" : cmd.ref().trim());
        row.setSubdirectory(nullToEmpty(cmd.subdirectory()));
        row.setEncryptedSecret(cmd.encryptedSecret());
        row.setLastCommitSha("");
        row.setStatus("READY");
        row.setLastError("");
        row.setRemark(nullToEmpty(cmd.remark()));
        row.setIsEnabled(SkillManageModels.normalize01(cmd.isEnabled(), 1));
        gitRepository.insertSource(row);
        return row;
    }

    @Transactional
    public AgentSkillGitSource softDeleteSource(Long id) {
        AgentSkillGitSource row = requireSource(id);
        long n = gitRepository.softDeleteSource(id);
        if (n == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source " + id + " not found");
        }
        return row;
    }

    public GitPreviewResult preview(Long sourceId) {
        AgentSkillGitSource source = requireSource(sourceId);
        GitSkillSourcePort.GitPreview preview =
                gitPort.preview(source.getUrl(), source.getRef(), source.getSubdirectory());
        List<GitPackagePreview> packages = preview.packages().stream()
                .map(p -> new GitPackagePreview(p.skillPath(), p.name(), p.description(), p.contentHash()))
                .toList();
        return new GitPreviewResult(preview.commitSha(), packages);
    }

    @Transactional
    public GitSyncResult sync(GitSyncCommand cmd) {
        AgentSkillGitSource source = requireSource(cmd.sourceId());
        String head = gitPort.resolveHead(source.getUrl(), source.getRef());
        if (cmd.expectedCommitSha() != null && !cmd.expectedCommitSha().isBlank()) {
            if (!cmd.expectedCommitSha().equals(head)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "expectedCommitSha does not match current HEAD");
            }
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int conflict = 0;
        int failed = 0;
        List<GitSyncItem> items = new ArrayList<>();

        for (String skillPath : cmd.skillPaths() == null ? List.<String>of() : cmd.skillPaths()) {
            try {
                GitSkillSourcePort.GitPackage pkg =
                        gitPort.readPackage(source.getUrl(), source.getRef(), source.getSubdirectory(), skillPath);
                AgentSkillGitSync existing = gitRepository.findSync(source.getId(), skillPath);
                if (existing != null
                        && existing.getCommitSha().equals(head)
                        && existing.getContentHash().equals(pkg.contentHash())) {
                    unchanged++;
                    items.add(new GitSyncItem(skillPath, "UNCHANGED", existing.getDraftId()));
                    continue;
                }
                // 活跃草稿冲突：同 name/visibility 已有非 CONSUMED 草稿且非本 sync 创建
                if (draftRepository.existsActive(source.getOwnerUserId(), pkg.name(), source.getScope(), null)) {
                    conflict++;
                    items.add(new GitSyncItem(skillPath, "CONFLICT", null));
                    continue;
                }

                AgentSkillDraft draft;
                boolean isNew = existing == null || existing.getDraftId() == null;
                if (isNew) {
                    draft = new AgentSkillDraft();
                    draft.setName(pkg.name());
                    draft.setSkillContent(pkg.skillContent());
                    draft.setVisibility(source.getScope());
                    draft.setStatus(SkillManageModels.STATUS_DRAFT);
                    draft.setOwnerUserId(source.getOwnerUserId());
                    draft.setContentHash(pkg.contentHash());
                    draft.setRemark("imported from git");
                    draft.setIsEnabled(1);
                    draftRepository.insert(draft);
                    created++;
                } else {
                    draft = draftRepository.findById(existing.getDraftId());
                    if (draft == null || draft.getStatus().equals(SkillManageModels.STATUS_CONSUMED)) {
                        conflict++;
                        items.add(new GitSyncItem(skillPath, "CONFLICT", null));
                        continue;
                    }
                    draft.setSkillContent(pkg.skillContent());
                    draft.setContentHash(pkg.contentHash());
                    draftRepository.update(draft);
                    updated++;
                }

                upsertSync(source.getId(), skillPath, head, pkg.contentHash(), draft.getId());
                items.add(new GitSyncItem(skillPath, isNew ? "CREATED" : "UPDATED", draft.getId()));
            } catch (Exception e) {
                failed++;
                items.add(new GitSyncItem(skillPath, "FAILED", null));
            }
        }

        source.setLastCommitSha(head);
        source.setLastSyncedAt(LocalDateTime.now());
        source.setStatus("READY");
        source.setLastError("");
        gitRepository.updateSource(source);

        return new GitSyncResult(head, created, updated, unchanged, conflict, failed, items);
    }

    private void upsertSync(Long sourceId, String skillPath, String commitSha, String contentHash, Long draftId) {
        AgentSkillGitSync existing = gitRepository.findSync(sourceId, skillPath);
        if (existing == null) {
            AgentSkillGitSync row = new AgentSkillGitSync();
            row.setSourceId(sourceId);
            row.setSkillPath(skillPath);
            row.setCommitSha(commitSha);
            row.setContentHash(contentHash);
            row.setDraftId(draftId);
            row.setDeletedAt(0L);
            gitRepository.insertSync(row);
        } else {
            existing.setCommitSha(commitSha);
            existing.setContentHash(contentHash);
            existing.setDraftId(draftId);
            existing.setDeletedAt(0L);
            gitRepository.updateSync(existing);
        }
    }

    private AgentSkillGitSource requireSource(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "sourceId 不能为空");
        }
        AgentSkillGitSource row = gitRepository.findSourceById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "git source " + id + " not found");
        }
        return row;
    }

    private static String requireScope(String raw) {
        String scope = raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
        if (scope == null || (!scope.equals("MARKET") && !scope.equals("PRIVATE"))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "scope must be MARKET|PRIVATE");
        }
        return scope;
    }

    private static String requireUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url is required");
        }
        String url = raw.trim();
        if (!url.startsWith("https://")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url must be HTTPS");
        }
        if (url.contains("@")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url must not contain user-info");
        }
        if (url.length() > 255) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url must be ≤ 255 chars");
        }
        return url;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
