package com.wshake.service.agent;

import com.wshake.common.constant.StatusFlags;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.common.time.TimeZones;
import com.wshake.service.agent.AgentSkillMarkdown.Frontmatter;
import com.wshake.service.agent.SkillControlModels.BindableSkillView;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftCommand;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftResourceCommand;
import com.wshake.service.agent.SkillControlModels.SkillDraftView;
import com.wshake.service.agent.SkillControlModels.SkillInstallView;
import com.wshake.service.agent.SkillControlModels.SkillMarketView;
import com.wshake.service.agent.SkillControlModels.SkillReleaseView;
import com.wshake.service.agent.SkillControlModels.SkillResourceView;
import com.wshake.service.agent.SkillControlModels.UpdateSkillDraftCommand;
import com.wshake.service.agent.SkillControlModels.UpdateSkillDraftResourceCommand;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillInstall;
import com.wshake.service.entity.AgentSkillMarket;
import com.wshake.service.entity.AgentSkillMarketResource;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.entity.AgentSkillReleaseResource;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillInstallRepository;
import com.wshake.service.repository.AgentSkillMarketRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillControlService {

    private final AgentSkillDraftRepository draftRepository;
    private final AgentSkillReleaseRepository releaseRepository;
    private final AgentSkillMarketRepository marketRepository;
    private final AgentSkillInstallRepository installRepository;

    @Transactional
    public SkillDraftView createDraft(CreateSkillDraftCommand command) {
        Long ownerUserId = requireUserId(command.ownerUserId());
        String visibility = requireVisibility(command.visibility());
        ParsedSkill parsed =
                parseSkill(command.name(), command.description(), command.skillContent(), command.resources());
        AgentSkillDraft existing = draftRepository.findActiveByOwnerAndName(ownerUserId, parsed.name());
        if (existing != null && !SkillControlModels.CONSUMED.equals(existing.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "an active draft already exists for this skill name");
        }
        AgentSkillDraft draft = new AgentSkillDraft();
        draft.setName(parsed.name());
        draft.setDescription(parsed.description());
        draft.setSkillContent(parsed.skillContent());
        draft.setVisibility(visibility);
        draft.setStatus(SkillControlModels.DRAFT);
        draft.setOwnerUserId(ownerUserId);
        draft.setBasedOnReleaseId(command.basedOnReleaseId());
        draft.setContentHash(parsed.contentHash());
        draft.setReviewComment("");
        draft.setReviewedBy(0L);
        draft.setRemark(nullable(command.remark()).trim());
        draft.setIsEnabled(StatusFlags.ENABLED);
        draftRepository.insert(draft);
        draftRepository.replaceResources(draft.getId(), toDraftResources(draft.getId(), parsed.resources()));
        return toDraftView(draft);
    }

    public List<SkillResourceView> listDraftResources(Long draftId, Long ownerUserId) {
        requireOwnedDraft(draftId, ownerUserId);
        return draftRepository.listResources(draftId).stream()
                .map(SkillControlService::toResourceView)
                .toList();
    }

    @Transactional
    public SkillResourceView createDraftResource(CreateSkillDraftResourceCommand command) {
        AgentSkillDraft draft = requireOwnedDraft(command.draftId(), command.ownerUserId());
        requireEditable(draft);
        AgentSkillMarkdown.validateResourcePath(command.path());
        String content = command.content() == null ? "" : command.content();
        if (draftRepository.findResourceByDraftIdAndPath(draft.getId(), command.path()) != null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "resource path already exists");
        }
        AgentSkillDraftResource row = new AgentSkillDraftResource();
        row.setDraftId(draft.getId());
        row.setResourcePath(command.path());
        row.setResourceContent(content);
        row.setContentHash(AgentSkillContentHash.sha256(content));
        draftRepository.insertResource(row);
        refreshDraftAfterResourceChange(draft);
        return toResourceView(row);
    }

    @Transactional
    public SkillResourceView updateDraftResource(UpdateSkillDraftResourceCommand command) {
        AgentSkillDraft draft = requireOwnedDraft(command.draftId(), command.ownerUserId());
        requireEditable(draft);
        AgentSkillDraftResource row = requireOwnedDraftResource(draft.getId(), command.resourceId());
        if (!command.pathPresent() && !command.contentPresent()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "path or content is required");
        }
        if (command.pathPresent()) {
            AgentSkillMarkdown.validateResourcePath(command.path());
            AgentSkillDraftResource conflict =
                    draftRepository.findResourceByDraftIdAndPath(draft.getId(), command.path());
            if (conflict != null && !conflict.getId().equals(row.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "resource path already exists");
            }
            row.setResourcePath(command.path());
        }
        if (command.contentPresent()) {
            String content = command.content() == null ? "" : command.content();
            row.setResourceContent(content);
            row.setContentHash(AgentSkillContentHash.sha256(content));
        }
        draftRepository.updateResource(row);
        refreshDraftAfterResourceChange(draft);
        return toResourceView(row);
    }

    @Transactional
    public void deleteDraftResource(Long draftId, Long resourceId, Long ownerUserId) {
        AgentSkillDraft draft = requireOwnedDraft(draftId, ownerUserId);
        requireEditable(draft);
        requireOwnedDraftResource(draft.getId(), resourceId);
        draftRepository.deleteResource(resourceId);
        refreshDraftAfterResourceChange(draft);
    }

    @Transactional
    public SkillDraftView updateDraft(UpdateSkillDraftCommand command) {
        AgentSkillDraft draft = requireOwnedDraft(command.id(), command.ownerUserId());
        requireEditable(draft);
        String skillContent = command.skillContentPresent() ? command.skillContent() : draft.getSkillContent();
        Map<String, String> resources = command.resourcesPresent()
                ? command.resources()
                : toDraftResourceMap(draftRepository.listResources(draft.getId()));
        String description = command.descriptionPresent() ? command.description() : draft.getDescription();
        ParsedSkill parsed = parseSkill(draft.getName(), description, skillContent, resources);
        draft.setDescription(parsed.description());
        draft.setSkillContent(parsed.skillContent());
        draft.setContentHash(parsed.contentHash());
        if (command.remarkPresent()) {
            draft.setRemark(nullable(command.remark()).trim());
        }
        if (SkillControlModels.REJECTED.equals(draft.getStatus())) {
            draft.setStatus(SkillControlModels.DRAFT);
        }
        draftRepository.update(draft);
        // 仅在显式提交 resources 时整表替换；只改 SKILL.md 时保留资源行 id，避免右侧按 resourceId 操作失效
        if (command.resourcesPresent()) {
            draftRepository.replaceResources(draft.getId(), toDraftResources(draft.getId(), parsed.resources()));
        }
        return toDraftView(draft);
    }

    @Transactional
    public SkillDraftView submit(Long draftId, Long ownerUserId) {
        AgentSkillDraft draft = requireOwnedDraft(draftId, ownerUserId);
        if (!SkillControlModels.DRAFT.equals(draft.getStatus())
                && !SkillControlModels.REJECTED.equals(draft.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only draft or rejected skills can be submitted");
        }
        draft.setStatus(SkillControlModels.PENDING_REVIEW);
        draftRepository.update(draft);
        return toDraftView(draft);
    }

    @Transactional
    public SkillDraftView withdraw(Long draftId, Long ownerUserId) {
        AgentSkillDraft draft = requireOwnedDraft(draftId, ownerUserId);
        if (!SkillControlModels.PENDING_REVIEW.equals(draft.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only pending review skills can be withdrawn");
        }
        draft.setStatus(SkillControlModels.DRAFT);
        draftRepository.update(draft);
        return toDraftView(draft);
    }

    @Transactional
    public SkillDraftView reject(Long draftId, String comment, Long reviewerUserId) {
        AgentSkillDraft draft = requireDraft(draftId);
        if (!SkillControlModels.PENDING_REVIEW.equals(draft.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only pending review skills can be rejected");
        }
        draft.setStatus(SkillControlModels.REJECTED);
        draft.setReviewComment(nullable(comment).trim());
        draft.setReviewedBy(requireUserId(reviewerUserId));
        draft.setReviewedAt(TimeZones.now());
        draftRepository.update(draft);
        return toDraftView(draft);
    }

    @Transactional
    public SkillReleaseView approve(Long draftId, Long reviewerUserId) {
        AgentSkillDraft draft = requireDraft(draftId);
        if (!SkillControlModels.PENDING_REVIEW.equals(draft.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only pending review skills can be published");
        }
        Map<String, String> resources = toDraftResourceMap(draftRepository.listResources(draft.getId()));
        ParsedSkill parsed = parseSkill(draft.getName(), draft.getDescription(), draft.getSkillContent(), resources);
        if (!parsed.contentHash().equals(draft.getContentHash())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill content hash does not match");
        }
        Long releaseOwnerId = SkillControlModels.VISIBILITY_MARKET.equals(draft.getVisibility())
                ? SkillControlModels.MARKET_OWNER_USER_ID
                : draft.getOwnerUserId();
        AgentSkillRelease latest = releaseRepository.findLatest(releaseOwnerId, draft.getVisibility(), draft.getName());
        int version = latest == null ? 1 : latest.getVersion() + 1;
        AgentSkillRelease release = new AgentSkillRelease();
        release.setName(draft.getName());
        release.setVersion(version);
        release.setDescription(draft.getDescription());
        release.setSkillContent(draft.getSkillContent());
        release.setVisibility(draft.getVisibility());
        release.setStatus(SkillControlModels.PUBLISHED);
        release.setOwnerUserId(releaseOwnerId);
        release.setSourceDraftId(draft.getId());
        release.setContentHash(draft.getContentHash());
        release.setSource(SkillControlModels.SOURCE_MYSQL);
        release.setRemark(draft.getRemark());
        release.setIsEnabled(StatusFlags.ENABLED);
        releaseRepository.insert(release);
        releaseRepository.insertResources(toReleaseResources(release.getId(), resources));
        draft.setStatus(SkillControlModels.CONSUMED);
        draft.setReviewedBy(requireUserId(reviewerUserId));
        draft.setReviewedAt(TimeZones.now());
        draftRepository.update(draft);
        if (SkillControlModels.VISIBILITY_MARKET.equals(draft.getVisibility())) {
            upsertMarket(release, resources);
        }
        return toReleaseView(release, resources);
    }

    @Transactional
    public SkillInstallView install(Long userId, String skillName) {
        Long ownerUserId = requireUserId(userId);
        AgentSkillMarkdown.validateSkillName(skillName);
        AgentSkillRelease latest = requirePublishedMarketRelease(skillName);
        AgentSkillInstall existing = installRepository.findActive(
                ownerUserId, skillName, SkillControlModels.VISIBILITY_MARKET, SkillControlModels.MARKET_OWNER_USER_ID);
        if (existing != null) {
            return toInstallView(existing, latest.getId());
        }
        AgentSkillInstall row = new AgentSkillInstall();
        row.setUserId(ownerUserId);
        row.setSkillName(skillName);
        row.setVisibility(SkillControlModels.VISIBILITY_MARKET);
        row.setOwnerUserId(SkillControlModels.MARKET_OWNER_USER_ID);
        row.setRemark("");
        row.setIsEnabled(StatusFlags.ENABLED);
        installRepository.insert(row);
        return toInstallView(row, latest.getId());
    }

    @Transactional
    public void uninstall(Long installId, Long userId) {
        AgentSkillInstall row = installRepository.findById(installId);
        if (row == null || (row.getDeletedAt() != null && row.getDeletedAt() != 0L)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill install not found");
        }
        if (!row.getUserId().equals(requireUserId(userId))) {
            throw BizException.of(ResultCode.AUTH_FORBIDDEN, "skill install is not owned by current user");
        }
        row.setDeletedAt(TimeZones.instant().toEpochMilli());
        installRepository.update(row);
    }

    @Transactional
    public SkillReleaseView deprecate(Long releaseId, Long operatorUserId) {
        requireUserId(operatorUserId);
        AgentSkillRelease release = requireRelease(releaseId);
        if (!SkillControlModels.PUBLISHED.equals(release.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only published releases can be deprecated");
        }
        release.setStatus(SkillControlModels.DEPRECATED);
        releaseRepository.update(release);
        return toReleaseView(release, toReleaseResourceMap(releaseRepository.listResources(release.getId())));
    }

    @Transactional
    public void unlistMarket(String name, Long operatorUserId) {
        requireUserId(operatorUserId);
        AgentSkillMarkdown.validateSkillName(name);
        AgentSkillMarket market = marketRepository.findByName(name);
        if (market == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "market skill not found");
        }
        AgentSkillRelease release = requireRelease(market.getCurrentReleaseId());
        marketRepository.deleteByName(name);
        if (SkillControlModels.PUBLISHED.equals(release.getStatus())) {
            release.setStatus(SkillControlModels.DEPRECATED);
            releaseRepository.update(release);
        }
    }

    public SkillDraftView getDraft(Long id, Long ownerUserId) {
        return toDraftView(requireOwnedDraft(id, ownerUserId));
    }

    public List<SkillDraftView> listDrafts(Long ownerUserId) {
        return draftRepository.listByOwnerUserId(requireUserId(ownerUserId)).stream()
                .map(this::toDraftView)
                .toList();
    }

    public SkillReleaseView getRelease(Long id) {
        AgentSkillRelease release = requireRelease(id);
        return toReleaseView(release, toReleaseResourceMap(releaseRepository.listResources(id)));
    }

    public List<SkillMarketView> listMarket() {
        return marketRepository.listAll().stream()
                .map(row -> new SkillMarketView(
                        row.getId(),
                        row.getName(),
                        row.getDescription(),
                        row.getContentHash(),
                        row.getCurrentReleaseId(),
                        row.getSource()))
                .toList();
    }

    public List<BindableSkillView> listBindable(Long userId) {
        Long ownerUserId = requireUserId(userId);
        List<BindableSkillView> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AgentSkillInstall install : installRepository.listByUserId(ownerUserId)) {
            AgentSkillRelease latest = releaseRepository.findLatest(
                    install.getOwnerUserId(), install.getVisibility(), install.getSkillName());
            if (latest == null || !SkillControlModels.PUBLISHED.equals(latest.getStatus())) {
                continue;
            }
            String key = latest.getVisibility() + ":" + latest.getOwnerUserId() + ":" + latest.getName();
            if (seen.add(key)) {
                result.add(toBindable(latest));
            }
        }
        for (AgentSkillRelease release :
                releaseRepository.listPublishedByOwner(ownerUserId, SkillControlModels.VISIBILITY_PRIVATE)) {
            String key = release.getVisibility() + ":" + release.getOwnerUserId() + ":" + release.getName();
            if (seen.add(key)) {
                result.add(toBindable(release));
            }
        }
        return result;
    }

    boolean canBind(Long userId, AgentSkillRelease release) {
        if (release == null
                || !SkillControlModels.PUBLISHED.equals(release.getStatus())
                || release.getIsEnabled() == null
                || release.getIsEnabled() == StatusFlags.DISABLED) {
            return false;
        }
        if (SkillControlModels.VISIBILITY_PRIVATE.equals(release.getVisibility())) {
            return release.getOwnerUserId().equals(userId);
        }
        return installRepository.findActive(
                        userId,
                        release.getName(),
                        SkillControlModels.VISIBILITY_MARKET,
                        SkillControlModels.MARKET_OWNER_USER_ID)
                != null;
    }

    AgentSkillRelease requirePublishedRelease(Long releaseId) {
        AgentSkillRelease release = requireRelease(releaseId);
        if (!SkillControlModels.PUBLISHED.equals(release.getStatus())
                || release.getIsEnabled() == null
                || release.getIsEnabled() == StatusFlags.DISABLED) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill release is not published");
        }
        return release;
    }

    AgentSkillRelease requireReleaseForRun(Long releaseId) {
        return requireRelease(releaseId);
    }

    Map<String, String> resourcesOf(Long releaseId) {
        return toReleaseResourceMap(releaseRepository.listResources(releaseId));
    }

    private void upsertMarket(AgentSkillRelease release, Map<String, String> resources) {
        AgentSkillMarket existing = marketRepository.findByName(release.getName());
        if (existing == null) {
            AgentSkillMarket row = new AgentSkillMarket();
            row.setName(release.getName());
            row.setDescription(release.getDescription());
            row.setSkillContent(release.getSkillContent());
            row.setSource(SkillControlModels.SOURCE_MYSQL);
            row.setCurrentReleaseId(release.getId());
            row.setOwnerUserId(SkillControlModels.MARKET_OWNER_USER_ID);
            row.setVisibility(SkillControlModels.VISIBILITY_MARKET);
            row.setContentHash(release.getContentHash());
            row.setRemark(release.getRemark());
            row.setIsEnabled(StatusFlags.ENABLED);
            row.setCreatedBy(0L);
            row.setUpdatedBy(0L);
            marketRepository.insert(row);
            marketRepository.replaceResources(row.getId(), toMarketResources(row.getId(), resources));
            return;
        }
        existing.setDescription(release.getDescription());
        existing.setSkillContent(release.getSkillContent());
        existing.setSource(SkillControlModels.SOURCE_MYSQL);
        existing.setCurrentReleaseId(release.getId());
        existing.setContentHash(release.getContentHash());
        existing.setRemark(release.getRemark());
        existing.setIsEnabled(StatusFlags.ENABLED);
        marketRepository.update(existing);
        marketRepository.replaceResources(existing.getId(), toMarketResources(existing.getId(), resources));
    }

    private ParsedSkill parseSkill(
            String requestedName, String requestedDescription, String skillContent, Map<String, String> resources) {
        AgentSkillMarkdown.validateSkillName(requestedName);
        if (skillContent == null || skillContent.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skillContent is required");
        }
        String content = skillContent;
        Frontmatter frontmatter = AgentSkillMarkdown.parse(content);
        if (!requestedName.equals(frontmatter.name())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must match SKILL.md frontmatter");
        }
        String description = requestedDescription == null || requestedDescription.isBlank()
                ? frontmatter.description()
                : requestedDescription.trim();
        if (!description.equals(frontmatter.description())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "description must match SKILL.md frontmatter");
        }
        Map<String, String> normalized = normalizeResources(resources);
        return new ParsedSkill(
                frontmatter.name(),
                description,
                content,
                normalized,
                AgentSkillContentHash.sha256(content, normalized));
    }

    private Map<String, String> normalizeResources(Map<String, String> resources) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (resources == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : resources.entrySet()) {
            AgentSkillMarkdown.validateResourcePath(entry.getKey());
            normalized.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return normalized;
    }

    private AgentSkillDraft requireOwnedDraft(Long id, Long ownerUserId) {
        AgentSkillDraft draft = requireDraft(id);
        if (!draft.getOwnerUserId().equals(requireUserId(ownerUserId))) {
            throw BizException.of(ResultCode.AUTH_FORBIDDEN, "skill draft is not owned by current user");
        }
        return draft;
    }

    private AgentSkillDraft requireDraft(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "draftId is required");
        }
        AgentSkillDraft draft = draftRepository.findById(id);
        if (draft == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill draft not found");
        }
        return draft;
    }

    private AgentSkillRelease requireRelease(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skillReleaseId is required");
        }
        AgentSkillRelease release = releaseRepository.findById(id);
        if (release == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill release not found");
        }
        return release;
    }

    private AgentSkillRelease requirePublishedMarketRelease(String name) {
        AgentSkillRelease latest = releaseRepository.findLatest(
                SkillControlModels.MARKET_OWNER_USER_ID, SkillControlModels.VISIBILITY_MARKET, name);
        if (latest == null || !SkillControlModels.PUBLISHED.equals(latest.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "market skill is not published");
        }
        return latest;
    }

    private static void requireEditable(AgentSkillDraft draft) {
        if (!SkillControlModels.DRAFT.equals(draft.getStatus())
                && !SkillControlModels.REJECTED.equals(draft.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only draft or rejected skills can be updated");
        }
    }

    private static String requireVisibility(String visibility) {
        if (SkillControlModels.VISIBILITY_MARKET.equals(visibility)
                || SkillControlModels.VISIBILITY_PRIVATE.equals(visibility)) {
            return visibility;
        }
        throw BizException.of(ResultCode.PARAM_INVALID, "visibility must be MARKET or PRIVATE");
    }

    private static Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw BizException.of(ResultCode.AUTH_NOT_LOGIN, "owner user is required");
        }
        return userId;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private void refreshDraftAfterResourceChange(AgentSkillDraft draft) {
        Map<String, String> resources = toDraftResourceMap(draftRepository.listResources(draft.getId()));
        draft.setContentHash(AgentSkillContentHash.sha256(draft.getSkillContent(), resources));
        if (SkillControlModels.REJECTED.equals(draft.getStatus())) {
            draft.setStatus(SkillControlModels.DRAFT);
        }
        draftRepository.update(draft);
    }

    private AgentSkillDraftResource requireOwnedDraftResource(Long draftId, Long resourceId) {
        if (resourceId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "resourceId is required");
        }
        AgentSkillDraftResource row = draftRepository.findResourceById(resourceId);
        if (row == null || !draftId.equals(row.getDraftId())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill draft resource not found");
        }
        return row;
    }

    private static SkillResourceView toResourceView(AgentSkillDraftResource row) {
        return new SkillResourceView(
                row.getId(), row.getResourcePath(), row.getResourceContent(), row.getContentHash());
    }

    private SkillDraftView toDraftView(AgentSkillDraft draft) {
        List<SkillResourceView> resources = draftRepository.listResources(draft.getId()).stream()
                .map(SkillControlService::toResourceView)
                .toList();
        return new SkillDraftView(
                draft.getId(),
                draft.getName(),
                draft.getDescription(),
                draft.getSkillContent(),
                draft.getVisibility(),
                draft.getStatus(),
                draft.getOwnerUserId(),
                draft.getBasedOnReleaseId(),
                draft.getContentHash(),
                draft.getReviewComment(),
                draft.getReviewedBy(),
                draft.getReviewedAt(),
                draft.getRemark(),
                resources,
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }

    private static SkillReleaseView toReleaseView(AgentSkillRelease release, Map<String, String> resources) {
        List<SkillResourceView> views = resources.entrySet().stream()
                .map(entry -> new SkillResourceView(
                        null, entry.getKey(), entry.getValue(), AgentSkillContentHash.sha256(entry.getValue())))
                .toList();
        return new SkillReleaseView(
                release.getId(),
                release.getName(),
                release.getVersion(),
                release.getDescription(),
                release.getSkillContent(),
                release.getVisibility(),
                release.getStatus(),
                release.getOwnerUserId(),
                release.getSourceDraftId(),
                release.getContentHash(),
                release.getSource(),
                release.getRemark(),
                views,
                release.getCreatedAt());
    }

    private static SkillInstallView toInstallView(AgentSkillInstall row, Long currentReleaseId) {
        return new SkillInstallView(
                row.getId(),
                row.getUserId(),
                row.getSkillName(),
                row.getVisibility(),
                row.getOwnerUserId(),
                currentReleaseId);
    }

    private static BindableSkillView toBindable(AgentSkillRelease release) {
        return new BindableSkillView(
                release.getId(),
                release.getName(),
                release.getVisibility(),
                release.getOwnerUserId(),
                release.getContentHash(),
                release.getVersion());
    }

    private static Map<String, String> toDraftResourceMap(List<AgentSkillDraftResource> rows) {
        Map<String, String> resources = new LinkedHashMap<>();
        for (AgentSkillDraftResource row : rows) {
            resources.put(row.getResourcePath(), row.getResourceContent());
        }
        return resources;
    }

    private static Map<String, String> toReleaseResourceMap(List<AgentSkillReleaseResource> rows) {
        Map<String, String> resources = new LinkedHashMap<>();
        for (AgentSkillReleaseResource row : rows) {
            resources.put(row.getResourcePath(), row.getResourceContent());
        }
        return resources;
    }

    private static List<AgentSkillDraftResource> toDraftResources(Long draftId, Map<String, String> resources) {
        List<AgentSkillDraftResource> rows = new ArrayList<>();
        resources.forEach((path, content) -> {
            AgentSkillDraftResource row = new AgentSkillDraftResource();
            row.setDraftId(draftId);
            row.setResourcePath(path);
            row.setResourceContent(content);
            row.setContentHash(AgentSkillContentHash.sha256(content));
            rows.add(row);
        });
        return rows;
    }

    private static List<AgentSkillReleaseResource> toReleaseResources(Long releaseId, Map<String, String> resources) {
        List<AgentSkillReleaseResource> rows = new ArrayList<>();
        resources.forEach((path, content) -> {
            AgentSkillReleaseResource row = new AgentSkillReleaseResource();
            row.setReleaseId(releaseId);
            row.setResourcePath(path);
            row.setResourceContent(content);
            row.setContentHash(AgentSkillContentHash.sha256(content));
            rows.add(row);
        });
        return rows;
    }

    private static List<AgentSkillMarketResource> toMarketResources(Long skillId, Map<String, String> resources) {
        List<AgentSkillMarketResource> rows = new ArrayList<>();
        resources.forEach((path, content) -> {
            AgentSkillMarketResource row = new AgentSkillMarketResource();
            row.setId(skillId);
            row.setResourcePath(path);
            row.setResourceContent(content);
            row.setContentHash(AgentSkillContentHash.sha256(content));
            rows.add(row);
        });
        return rows;
    }

    private record ParsedSkill(
            String name, String description, String skillContent, Map<String, String> resources, String contentHash) {}
}
