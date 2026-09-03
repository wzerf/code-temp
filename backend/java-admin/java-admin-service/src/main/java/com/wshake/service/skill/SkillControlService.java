package com.wshake.service.skill;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.constant.PageLimits;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.entity.AgentSkillGitSync;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.entity.AgentSkillReleaseResource;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillDraftResourceRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillReleaseResourceRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill 控制面服务：草稿 → 提交 → 审核 → 不可变 Release,市场列表由 Release 派生。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class SkillControlService {

    private static final String VIS_MARKET = "MARKET";
    private static final String VIS_PRIVATE = "PRIVATE";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONSUMED = "CONSUMED";

    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_DEPRECATED = "DEPRECATED";

    private final AgentSkillDraftRepository draftRepository;
    private final AgentSkillDraftResourceRepository draftResourceRepository;
    private final AgentSkillReleaseRepository releaseRepository;
    private final AgentSkillReleaseResourceRepository releaseResourceRepository;
    private final AgentSkillGitSourceRepository gitSourceRepository;
    private final AgentSkillGitSyncRepository gitSyncRepository;
    private final Converter converter;

    // ---------- 草稿 ----------

    public PageData<SkillView> pageDraft(SkillListQuery q) {
        EasyPageResult<AgentSkillDraft> page = draftRepository.page(
                q.page(), q.pageSize(), ownerFilter(q.ownerUserId()), q.nameLike(), q.visibility(), q.status());
        List<AgentSkillDraft> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        List<Long> ids = rows.stream().map(AgentSkillDraft::getId).toList();
        return PageData.of(toDraftViewsBatch(rows, ids), page.getTotal());
    }

    public SkillView getDraft(Long id) {
        return toDraftViewFull(requireDraft(id));
    }

    /** 批量组装草稿视图(资源数 + Git 来源分组;避免 N+1)。 */
    private List<SkillView> toDraftViewsBatch(List<AgentSkillDraft> rows, List<Long> ids) {
        Map<Long, Long> counts = draftResourceRepository.countByDraftIds(ids);
        List<AgentSkillGitSync> syncs = gitSyncRepository.listByDraftIds(ids);
        Map<Long, Long> draftToSource = new LinkedHashMap<>();
        for (AgentSkillGitSync sync : syncs) {
            draftToSource.putIfAbsent(sync.getDraftId(), sync.getSourceId());
        }
        Map<Long, String> sourceNames = new LinkedHashMap<>();
        if (!draftToSource.isEmpty()) {
            for (AgentSkillGitSource s : gitSourceRepository.listByIds(draftToSource.values())) {
                sourceNames.put(s.getId(), shortUrl(s.getUrl()));
            }
        }
        List<SkillView> views = new ArrayList<>();
        for (AgentSkillDraft row : rows) {
            long count = counts.getOrDefault(row.getId(), 0L);
            Long sourceId = draftToSource.get(row.getId());
            String group = sourceId == null ? "" : sourceNames.getOrDefault(sourceId, "");
            views.add(toDraftView(row, (int) count, group));
        }
        return views;
    }

    /** 草稿实体 → 视图(基础字段 + 已算好的 count/group)。 */
    private SkillView toDraftView(AgentSkillDraft row, int resourceCount, String groupKey) {
        return new SkillView(
                row.getId(),
                row.getOwnerUserId(),
                row.getName(),
                row.getVisibility(),
                row.getStatus(),
                row.getDescription(),
                row.getSkillContent(),
                row.getContentHash(),
                row.getBasedOnReleaseId(),
                row.getReviewComment(),
                row.getReviewedBy(),
                row.getReviewedAt(),
                row.getRemark(),
                row.getIsEnabled(),
                row.getDeletedAt() == null ? 0L : row.getDeletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy() == null ? 0L : row.getCreatedBy(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                resourceCount,
                groupKey == null ? "" : groupKey);
    }

    /** 单草稿 → 视图(资源数 + Git 来源分组)。 */
    private SkillView toDraftViewFull(AgentSkillDraft row) {
        List<SkillView> views = toDraftViewsBatch(List.of(row), List.of(row.getId()));
        return views.isEmpty() ? toDraftView(row, 0, "") : views.get(0);
    }

    /** 草稿资源（含 SKILL.md 主内容）。 */
    public SkillResourceBundle getDraftBundle(Long id) {
        AgentSkillDraft draft = requireDraft(id);
        List<AgentSkillDraftResource> resources = draftResourceRepository.listByDraftId(id);
        List<ResourceView> views = resources.stream()
                .map(r -> new ResourceView(r.getResourcePath(), r.getContent()))
                .toList();
        return new SkillResourceBundle(draft.getSkillContent(), views);
    }

    @Transactional
    public SkillView createDraft(CreateSkillCommand cmd) {
        String name = requireName(cmd.name());
        String visibility = requireVisibility(cmd.visibility());
        Long owner = resolveOwner(cmd.ownerUserId());
        if (draftRepository.existsActiveDraft(owner, name, visibility, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/visibility 已存在活跃草稿");
        }
        AgentSkillDraft row = new AgentSkillDraft();
        row.setOwnerUserId(owner);
        row.setName(name);
        row.setVisibility(visibility);
        row.setStatus(STATUS_DRAFT);
        row.setDescription(clip(cmd.description(), 512));
        row.setSkillContent(cmd.skillContent() == null ? "" : cmd.skillContent());
        row.setContentHash("");
        row.setRemark(clip(cmd.remark(), 512));
        row.setIsEnabled(1);
        draftRepository.insert(row);
        // 初始资源
        saveResources(row.getId(), cmd.resources());
        refreshHash(row.getId());
        return toDraftViewFull(requireDraft(row.getId()));
    }

    @Transactional
    public SkillView updateDraft(Long id, UpdateSkillCommand cmd) {
        AgentSkillDraft row = requireDraft(id);
        requireEditable(row);
        if (cmd.name() != null) {
            String name = requireName(cmd.name());
            if (!name.equals(row.getName())
                    && draftRepository.existsActiveDraft(row.getOwnerUserId(), name, row.getVisibility(), id)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/visibility 已存在活跃草稿");
            }
            row.setName(name);
        }
        if (cmd.description() != null) {
            row.setDescription(clip(cmd.description(), 512));
        }
        if (cmd.skillContent() != null) {
            row.setSkillContent(cmd.skillContent());
        }
        if (cmd.remark() != null) {
            row.setRemark(clip(cmd.remark(), 512));
        }
        if (cmd.resources() != null) {
            saveResources(id, cmd.resources());
        }
        if (cmd.name() != null || cmd.description() != null || cmd.skillContent() != null || cmd.resources() != null) {
            refreshHash(id);
        }
        return toDraftViewFull(requireDraft(id));
    }

    @Transactional
    public void saveResources(Long draftId, List<ResourceInput> resources) {
        AgentSkillDraft draft = requireDraft(draftId);
        List<AgentSkillDraftResource> rows = new ArrayList<>();
        if (resources != null) {
            Map<String, String> seen = new LinkedHashMap<>();
            for (ResourceInput in : resources) {
                String path = SkillContentHasher.normalizeResourcePath(in.resourcePath());
                if (path == null) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "resourcePath 非法:" + in.resourcePath());
                }
                seen.putIfAbsent(path, in.content());
            }
            for (Map.Entry<String, String> e : seen.entrySet()) {
                AgentSkillDraftResource r = new AgentSkillDraftResource();
                r.setDraftId(draftId);
                r.setResourcePath(e.getKey());
                r.setContent(e.getValue() == null ? "" : e.getValue());
                rows.add(r);
            }
        }
        draftResourceRepository.deleteByDraftId(draftId);
        draftResourceRepository.insertAll(rows);
        // 资源变化影响 draft 行 hash,但 draft 尚未持久化于本方法:由调用方统一 refresh
    }

    @Transactional
    public void submit(Long id) {
        AgentSkillDraft row = requireDraft(id);
        requireStatus(row, STATUS_DRAFT, STATUS_REJECTED);
        if (row.getSkillContent() == null || row.getSkillContent().isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "SKILL.md 内容为空,不可提交");
        }
        draftRepository.updateStatus(id, STATUS_PENDING_REVIEW, "", null, null);
    }

    @Transactional
    public void withdraw(Long id) {
        AgentSkillDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_DRAFT, "", null, null);
    }

    @Transactional
    public void reject(Long id, String reason) {
        AgentSkillDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_REJECTED, clip(reason, 512), null, null);
    }

    /**
     * 通过审核：插入不可变 Release（version = max+1）,资源冻结拷贝,草稿置 CONSUMED。
     */
    @Transactional
    public SkillReleaseView approve(Long id) {
        AgentSkillDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        if (row.getSkillContent() == null || row.getSkillContent().isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "SKILL.md 内容为空");
        }
        List<AgentSkillDraftResource> resources = draftResourceRepository.listByDraftId(id);

        int nextVersion = nextVersion(row.getOwnerUserId(), row.getVisibility(), row.getName());
        AgentSkillRelease release = new AgentSkillRelease();
        release.setOwnerUserId(row.getOwnerUserId());
        release.setName(row.getName());
        release.setVisibility(row.getVisibility());
        release.setStatus(RELEASE_PUBLISHED);
        release.setVersion(nextVersion);
        release.setDescription(row.getDescription());
        release.setSkillContent(row.getSkillContent());
        release.setContentHash(row.getContentHash());
        release.setSourceDraftId(id);
        release.setRemark(row.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);
        Long releaseId = release.getId();

        if (!resources.isEmpty()) {
            List<AgentSkillReleaseResource> frozen = new ArrayList<>();
            for (AgentSkillDraftResource r : resources) {
                AgentSkillReleaseResource rr = new AgentSkillReleaseResource();
                rr.setReleaseId(releaseId);
                rr.setResourcePath(r.getResourcePath());
                rr.setContent(r.getContent());
                rr.setContentHash(r.getContentHash());
                frozen.add(rr);
            }
            releaseResourceRepository.insertAll(frozen);
        }

        draftRepository.updateStatus(id, STATUS_CONSUMED, "", null, null);
        return getRelease(releaseId);
    }

    @Transactional
    public void softDeleteDraft(Long id) {
        requireDraft(id);
        draftRepository.softDeleteById(id);
    }

    // ---------- Release / 市场 ----------

    public PageData<SkillReleaseView> pageRelease(ReleaseListQuery q) {
        EasyPageResult<AgentSkillRelease> page =
                releaseRepository.page(q.page(), q.pageSize(), q.visibility(), q.status(), q.nameLike());
        List<AgentSkillRelease> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        return PageData.of(toReleaseViewsWithCounts(rows), page.getTotal());
    }

    public SkillReleaseView getRelease(Long id) {
        AgentSkillRelease row = requireRelease(id);
        long count = releaseResourceRepository
                .countByReleaseIds(List.of(row.getId()))
                .getOrDefault(row.getId(), 0L);
        return toReleaseView(row, (int) count);
    }

    /** Release 内容包(SKILL.md 全文 + 冻结资源)。 */
    public SkillResourceBundle getReleaseBundle(Long id) {
        AgentSkillRelease release = requireRelease(id);
        List<AgentSkillReleaseResource> resources = releaseResourceRepository.listByReleaseId(id);
        List<ResourceView> views = resources.stream()
                .map(r -> new ResourceView(r.getResourcePath(), r.getContent()))
                .toList();
        return new SkillResourceBundle(release.getSkillContent(), views);
    }

    /** 市场列表 = MARKET + PUBLISHED,按 name 取 version 最大。 */
    public List<SkillReleaseView> listMarket() {
        Map<String, AgentSkillRelease> latest = new LinkedHashMap<>();
        for (AgentSkillRelease row : releaseRepository.listMarket()) {
            AgentSkillRelease existing = latest.get(row.getName());
            if (existing == null || row.getVersion() > existing.getVersion()) {
                latest.put(row.getName(), row);
            }
        }
        return toReleaseViewsWithCounts(new ArrayList<>(latest.values()));
    }

    /** 单个 Release 弃用（不可再被新 Binding 选用）。 */
    @Transactional
    public void deprecate(Long id) {
        AgentSkillRelease row = requireRelease(id);
        releaseRepository.updateStatus(id, RELEASE_DEPRECATED);
    }

    /** 市场下架 = 把该 name 的 MARKET PUBLISHED 最高版本 Release 置 DEPRECATED（退出市场可见集）。 */
    @Transactional
    public void takeDownMarket(Long id) {
        AgentSkillRelease row = requireRelease(id);
        if (!VIS_MARKET.equals(row.getVisibility()) || !RELEASE_PUBLISHED.equals(row.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "仅 MARKET 且 PUBLISHED 的 Release 可下架");
        }
        releaseRepository.updateStatus(id, RELEASE_DEPRECATED);
    }

    public List<SkillReleaseView> listBindable(Long ownerUserId) {
        List<AgentSkillRelease> all = releaseRepository.listMarket();
        Map<String, AgentSkillRelease> latest = new LinkedHashMap<>();
        for (AgentSkillRelease row : all) {
            AgentSkillRelease existing = latest.get(row.getName());
            if (existing == null || row.getVersion() > existing.getVersion()) {
                latest.put(row.getName(), row);
            }
        }
        return toReleaseViewsWithCounts(new ArrayList<>(latest.values()));
    }

    /** 批量给 Release 行补资源数并组装视图。 */
    private List<SkillReleaseView> toReleaseViewsWithCounts(List<AgentSkillRelease> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(AgentSkillRelease::getId).toList();
        Map<Long, Long> counts = releaseResourceRepository.countByReleaseIds(ids);
        List<SkillReleaseView> views = new ArrayList<>();
        for (AgentSkillRelease row : rows) {
            Long count = counts.getOrDefault(row.getId(), 0L);
            views.add(toReleaseView(row, count.intValue()));
        }
        return views;
    }

    // ---------- 内部 ----------

    private void refreshHash(Long draftId) {
        AgentSkillDraft row = requireDraft(draftId);
        List<AgentSkillDraftResource> resources = draftResourceRepository.listByDraftId(draftId);
        List<SkillContentHasher.ResourceEntry> entries = resources.stream()
                .map(r -> new SkillContentHasher.ResourceEntry(r.getResourcePath(), r.getContent()))
                .toList();
        String hash = SkillContentHasher.hash(row.getSkillContent(), entries);
        row.setContentHash(hash);
        draftRepository.update(row);
    }

    private int nextVersion(Long owner, String visibility, String name) {
        List<AgentSkillRelease> history = releaseRepository.listByNameAllVersions(owner, visibility, name);
        int max = 0;
        for (AgentSkillRelease r : history) {
            if (r.getVersion() != null && r.getVersion() > max) {
                max = r.getVersion();
            }
        }
        return max + 1;
    }

    private SkillReleaseView toReleaseView(AgentSkillRelease row, int resourceCount) {
        return new SkillReleaseView(
                row.getId(),
                row.getOwnerUserId(),
                row.getName(),
                row.getVisibility(),
                row.getStatus(),
                row.getVersion(),
                row.getDescription(),
                row.getSkillContent(),
                row.getContentHash(),
                row.getSourceDraftId(),
                row.getRemark(),
                row.getIsEnabled(),
                row.getDeletedAt() == null ? 0L : row.getDeletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy() == null ? 0L : row.getCreatedBy(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                resourceCount);
    }

    /** URL 缩略分组名:host/尾路径段。 */
    private static String shortUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String u = url.trim();
        try {
            java.net.URI uri = java.net.URI.create(u);
            String host = uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("\\.git$", "");
            String tail = path;
            int slash = path.lastIndexOf('/');
            if (slash >= 0) {
                tail = path.substring(slash + 1);
            }
            return tail.isEmpty() ? host : tail;
        } catch (Exception e) {
            return u;
        }
    }

    private AgentSkillDraft requireDraft(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentSkillDraft row = draftRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill draft " + id + " not found");
        }
        return row;
    }

    private AgentSkillRelease requireRelease(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentSkillRelease row = releaseRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill release " + id + " not found");
        }
        return row;
    }

    private static void requireEditable(AgentSkillDraft row) {
        if (!STATUS_DRAFT.equals(row.getStatus()) && !STATUS_REJECTED.equals(row.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "仅 DRAFT/REJECTED 草稿可编辑");
        }
    }

    private static void requireStatus(AgentSkillDraft row, String... allowed) {
        for (String s : allowed) {
            if (s.equals(row.getStatus())) {
                return;
            }
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID, "skill draft " + row.getId() + " 状态 " + row.getStatus() + " 不允许该操作");
    }

    private static String requireName(String raw) {
        String name = raw == null ? null : raw.trim();
        if (name == null || name.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        if (name.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 128 chars");
        }
        return name;
    }

    private static String requireVisibility(String raw) {
        String v = raw == null ? null : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!VIS_MARKET.equals(v) && !VIS_PRIVATE.equals(v)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "visibility must be MARKET|PRIVATE");
        }
        return v;
    }

    private static Long resolveOwner(Long ownerUserId) {
        return ownerUserId != null && ownerUserId > 0 ? ownerUserId : 0L;
    }

    private static Long ownerFilter(Long ownerUserId) {
        return ownerUserId != null && ownerUserId > 0 ? ownerUserId : null;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    // ---------- 领域模型 ----------

    public record SkillListQuery(
            int page, int pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
        public static SkillListQuery of(
                Integer page, Integer pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
            return new SkillListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    ownerUserId,
                    trimToNull(nameLike),
                    upperToNull(visibility),
                    upperToNull(status));
        }
    }

    public record CreateSkillCommand(
            String name,
            String description,
            String skillContent,
            String visibility,
            String remark,
            Long ownerUserId,
            List<ResourceInput> resources) {}

    public record UpdateSkillCommand(
            String name, String description, String skillContent, String remark, List<ResourceInput> resources) {}

    public record ResourceInput(String resourcePath, String content) {}

    public record ResourceView(String resourcePath, String content) {}

    public record SkillResourceBundle(String skillContent, List<ResourceView> resources) {}

    @io.github.linpeilie.annotations.AutoMapper(target = AgentSkillDraft.class)
    public record SkillView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            String status,
            String description,
            String skillContent,
            String contentHash,
            Long basedOnReleaseId,
            String reviewComment,
            Long reviewedBy,
            java.time.LocalDateTime reviewedAt,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            int resourceCount,
            String groupKey) {
        public SkillView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            contentHash = contentHash == null ? "" : contentHash;
            reviewComment = reviewComment == null ? "" : reviewComment;
            reviewedBy = reviewedBy == null ? 0L : reviewedBy;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
            groupKey = groupKey == null ? "" : groupKey;
        }
    }

    public record ReleaseListQuery(int page, int pageSize, String visibility, String status, String nameLike) {
        public static ReleaseListQuery of(
                Integer page, Integer pageSize, String visibility, String status, String nameLike) {
            return new ReleaseListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    upperToNull(visibility),
                    upperToNull(status),
                    trimToNull(nameLike));
        }
    }

    @io.github.linpeilie.annotations.AutoMapper(target = AgentSkillRelease.class)
    public record SkillReleaseView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            String status,
            Integer version,
            String description,
            String skillContent,
            String contentHash,
            Long sourceDraftId,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            int resourceCount) {
        public SkillReleaseView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            contentHash = contentHash == null ? "" : contentHash;
            sourceDraftId = sourceDraftId == null ? 0L : sourceDraftId;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static String upperToNull(String value) {
        String v = trimToNull(value);
        return v == null ? null : v.toUpperCase(java.util.Locale.ROOT);
    }
}
