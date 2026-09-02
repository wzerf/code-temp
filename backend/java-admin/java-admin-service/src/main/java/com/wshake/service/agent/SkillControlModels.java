package com.wshake.service.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SkillControlModels {

    private SkillControlModels() {}

    public static final String VISIBILITY_MARKET = "MARKET";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String REJECTED = "REJECTED";
    public static final String CONSUMED = "CONSUMED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DEPRECATED = "DEPRECATED";
    public static final String SOURCE_MYSQL = "mysql";
    public static final long MARKET_OWNER_USER_ID = 0L;

    public record SkillResourceView(Long id, String path, String content, String contentHash) {}

    public record CreateSkillDraftResourceCommand(Long draftId, Long ownerUserId, String path, String content) {}

    public record UpdateSkillDraftResourceCommand(
            Long draftId,
            Long resourceId,
            Long ownerUserId,
            String path,
            boolean pathPresent,
            String content,
            boolean contentPresent) {}

    public record CreateSkillDraftCommand(
            Long ownerUserId,
            String name,
            String description,
            String skillContent,
            String visibility,
            Map<String, String> resources,
            Long basedOnReleaseId,
            String remark) {}

    public record UpdateSkillDraftCommand(
            Long id,
            Long ownerUserId,
            String description,
            boolean descriptionPresent,
            String skillContent,
            boolean skillContentPresent,
            Map<String, String> resources,
            boolean resourcesPresent,
            String remark,
            boolean remarkPresent) {}

    public record SkillDraftView(
            Long id,
            String name,
            String description,
            String skillContent,
            String visibility,
            String status,
            Long ownerUserId,
            Long basedOnReleaseId,
            String contentHash,
            String reviewComment,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            String remark,
            List<SkillResourceView> resources,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record SkillReleaseView(
            Long id,
            String name,
            Integer version,
            String description,
            String skillContent,
            String visibility,
            String status,
            Long ownerUserId,
            Long sourceDraftId,
            String contentHash,
            String source,
            String remark,
            List<SkillResourceView> resources,
            LocalDateTime createdAt) {}

    public record SkillMarketView(
            Long id, String name, String description, String contentHash, Long currentReleaseId, String source) {}

    public record SkillInstallView(
            Long id, Long userId, String skillName, String visibility, Long ownerUserId, Long currentReleaseId) {}

    public record BindableSkillView(
            Long skillReleaseId,
            String name,
            String visibility,
            Long ownerUserId,
            String contentHash,
            Integer version) {}
}
