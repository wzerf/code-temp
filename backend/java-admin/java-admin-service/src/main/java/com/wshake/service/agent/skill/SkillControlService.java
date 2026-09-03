package com.wshake.service.agent.skill;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.skill.SkillManageModels.CreateSkillDraftCommand;
import com.wshake.service.agent.skill.SkillManageModels.ReviewCommand;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftListQuery;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftResourceView;
import com.wshake.service.agent.skill.SkillManageModels.SkillDraftView;
import com.wshake.service.agent.skill.SkillManageModels.SkillReleaseView;
import com.wshake.service.agent.skill.SkillManageModels.SkillResourceCommand;
import com.wshake.service.agent.skill.SkillManageModels.UpdateSkillDraftCommand;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.entity.AgentSkillReleaseResource;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillResourceRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill 控制面 Service：草稿/审核/Release/市场派生/Binding 快照。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class SkillControlService {

    private final AgentSkillDraftRepository draftRepository;
    private final AgentSkillReleaseRepository releaseRepository;
    private final AgentSkillResourceRepository resourceRepository;
    private final Converter converter;

    public PageData<SkillDraftView> pageDrafts(SkillDraftListQuery query) {
        EasyPageResult<AgentSkillDraft> page = draftRepository.page(
                query.page(), query.pageSize(), query.ownerUserId(), query.name(), query.visibility(), query.status());
        List<AgentSkillDraft> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(converter.convert(rows, SkillDraftView.class), page.getTotal());
    }

    public SkillDraftView getDraft(Long id) {
        return converter.convert(requireDraft(id), SkillDraftView.class);
    }

    @Transactional
    public SkillDraftView createDraft(CreateSkillDraftCommand cmd) {
        String name = requireName(cmd.name());
        String visibility = requireVisibility(cmd.visibility());
        Long owner = cmd.ownerUserId() == null ? 0L : cmd.ownerUserId();
        if (draftRepository.existsActive(owner, name, visibility, null)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "active skill draft already exists for same owner/name/visibility");
        }

        AgentSkillDraft row = new AgentSkillDraft();
        row.setName(name);
        row.setSkillContent(nullToEmpty(cmd.skillContent()));
        row.setVisibility(visibility);
        row.setStatus(SkillManageModels.STATUS_DRAFT);
        row.setOwnerUserId(owner);
        row.setBasedOnReleaseId(cmd.basedOnReleaseId());
        row.setContentHash(SkillManageModels.contentHash(cmd.skillContent(), List.of()));
        row.setReviewComment("");
        row.setReviewedBy(0L);
        row.setRemark(nullToEmpty(cmd.remark()));
        row.setIsEnabled(SkillManageModels.normalize01(cmd.isEnabled(), 1));
        draftRepository.insert(row);
        return converter.convert(requireDraft(row.getId()), SkillDraftView.class);
    }

    @Transactional
    public SkillDraftView updateDraft(UpdateSkillDraftCommand cmd) {
        AgentSkillDraft row = requireDraft(cmd.id());
        if (row.getStatus().equals(SkillManageModels.STATUS_CONSUMED)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "consumed draft cannot be updated");
        }
        if (cmd.name() != null) {
            row.setName(requireName(cmd.name()));
        }
        if (cmd.skillContent() != null) {
            row.setSkillContent(cmd.skillContent());
            row.setContentHash(SkillManageModels.contentHash(cmd.skillContent(), currentResources(cmd.id())));
        }
        if (cmd.visibility() != null) {
            row.setVisibility(requireVisibility(cmd.visibility()));
        }
        if (cmd.ownerUserId() != null) {
            row.setOwnerUserId(cmd.ownerUserId());
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        if (cmd.isEnabled() != null) {
            row.setIsEnabled(SkillManageModels.normalize01(cmd.isEnabled(), 1));
        }
        draftRepository.update(row);
        return converter.convert(requireDraft(row.getId()), SkillDraftView.class);
    }

    @Transactional
    public SkillDraftView softDelete(Long id) {
        AgentSkillDraft row = requireDraft(id);
        SkillDraftView snapshot = converter.convert(row, SkillDraftView.class);
        long n = draftRepository.softDeleteById(id);
        if (n == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill draft " + id + " not found");
        }
        return snapshot;
    }

    // ---------- resources ----------

    public List<SkillDraftResourceView> getResources(Long draftId) {
        requireDraft(draftId);
        return resourceRepository.listDraftResources(draftId).stream()
                .map(SkillDraftResourceView::from)
                .toList();
    }

    @Transactional
    public List<SkillDraftResourceView> setResources(Long draftId, List<SkillResourceCommand> resources) {
        AgentSkillDraft draft = requireDraft(draftId);
        if (draft.getStatus().equals(SkillManageModels.STATUS_CONSUMED)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "consumed draft cannot be updated");
        }
        resourceRepository.deleteDraftResources(draftId);
        List<SkillResourceCommand> list = resources == null ? List.of() : resources;
        for (SkillResourceCommand r : list) {
            SkillManageModels.requireResourcePath(r.resourcePath());
            AgentSkillDraftResource row = new AgentSkillDraftResource();
            row.setDraftId(draftId);
            row.setResourcePath(r.resourcePath().trim());
            row.setContent(r.content());
            resourceRepository.insertDraftResource(row);
        }
        draft.setContentHash(SkillManageModels.contentHash(draft.getSkillContent(), list));
        draftRepository.update(draft);
        return getResources(draftId);
    }

    // ---------- review / release ----------

    @Transactional
    public SkillDraftView submit(Long id) {
        AgentSkillDraft row = requireDraft(id);
        if (!row.getStatus().equals(SkillManageModels.STATUS_DRAFT)
                && !row.getStatus().equals(SkillManageModels.STATUS_REJECTED)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only DRAFT/REJECTED can be submitted");
        }
        row.setStatus(SkillManageModels.STATUS_PENDING_REVIEW);
        draftRepository.update(row);
        return converter.convert(requireDraft(id), SkillDraftView.class);
    }

    @Transactional
    public SkillReleaseView review(Long id, ReviewCommand cmd) {
        AgentSkillDraft draft = requireDraft(id);
        if (!draft.getStatus().equals(SkillManageModels.STATUS_PENDING_REVIEW)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only PENDING_REVIEW can be reviewed");
        }
        String action = cmd.action() == null ? "" : cmd.action().trim().toLowerCase(Locale.ROOT);
        if ("reject".equals(action)) {
            draft.setStatus(SkillManageModels.STATUS_REJECTED);
            draft.setReviewComment(nullToEmpty(cmd.comment()));
            draftRepository.update(draft);
            throw BizException.of(ResultCode.PARAM_INVALID, "draft rejected: " + draft.getReviewComment());
        }
        if (!"approve".equals(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be approve|reject");
        }

        // 插入不可变 Release，version 递增，草稿置 CONSUMED
        int version = releaseRepository.maxVersion(draft.getOwnerUserId(), draft.getVisibility(), draft.getName()) + 1;
        AgentSkillRelease release = new AgentSkillRelease();
        release.setOwnerUserId(draft.getOwnerUserId());
        release.setName(draft.getName());
        release.setVisibility(draft.getVisibility());
        release.setVersion(version);
        release.setStatus(SkillManageModels.RELEASE_PUBLISHED);
        release.setSourceDraftId(draft.getId());
        release.setSkillContent(draft.getSkillContent());
        release.setContentHash(draft.getContentHash());
        release.setRemark(draft.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);

        for (AgentSkillDraftResource r : resourceRepository.listDraftResources(draft.getId())) {
            AgentSkillReleaseResource rr = new AgentSkillReleaseResource();
            rr.setReleaseId(release.getId());
            rr.setResourcePath(r.getResourcePath());
            rr.setContent(r.getContent());
            resourceRepository.insertReleaseResource(rr);
        }

        draft.setStatus(SkillManageModels.STATUS_CONSUMED);
        draft.setReviewComment(nullToEmpty(cmd.comment()));
        draftRepository.update(draft);

        return converter.convert(requireRelease(release.getId()), SkillReleaseView.class);
    }

    public PageData<SkillReleaseView> pageReleases(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {
        EasyPageResult<AgentSkillRelease> result =
                releaseRepository.page(page, pageSize, ownerUserId, name, visibility, status);
        List<AgentSkillRelease> rows = result.getData() == null ? List.of() : result.getData();
        return PageData.of(converter.convert(rows, SkillReleaseView.class), result.getTotal());
    }

    public SkillReleaseView getRelease(Long id) {
        return converter.convert(requireRelease(id), SkillReleaseView.class);
    }

    /**
     * 市场列表：MARKET + PUBLISHED，按 name 取 version 最大的一条。
     */
    public List<SkillReleaseView> market() {
        List<AgentSkillRelease> all = releaseRepository.listMarket();
        Map<String, AgentSkillRelease> latest = new java.util.LinkedHashMap<>();
        for (AgentSkillRelease r : all) {
            AgentSkillRelease cur = latest.get(r.getName());
            if (cur == null || r.getVersion() > cur.getVersion()) {
                latest.put(r.getName(), r);
            }
        }
        return converter.convert(new ArrayList<>(latest.values()), SkillReleaseView.class);
    }

    @Transactional
    public SkillReleaseView deprecate(Long id) {
        AgentSkillRelease release = requireRelease(id);
        releaseRepository.updateStatus(id, SkillManageModels.RELEASE_DEPRECATED);
        return converter.convert(requireRelease(id), SkillReleaseView.class);
    }

    // ---------- helpers ----------

    private List<SkillResourceCommand> currentResources(Long draftId) {
        return resourceRepository.listDraftResources(draftId).stream()
                .map(r -> new SkillResourceCommand(r.getResourcePath(), r.getContent()))
                .toList();
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

    private static String requireName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        String name = raw.trim();
        if (name.length() > 64) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
        }
        return name;
    }

    private static String requireVisibility(String raw) {
        String v = SkillManageModels.normalizeEnum(raw);
        if (v == null
                || (!v.equals(SkillManageModels.VISIBILITY_MARKET)
                        && !v.equals(SkillManageModels.VISIBILITY_PRIVATE))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "visibility must be MARKET|PRIVATE");
        }
        return v;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
