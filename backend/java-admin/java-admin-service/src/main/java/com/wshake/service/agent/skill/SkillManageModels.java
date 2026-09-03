package com.wshake.service.agent.skill;

import com.wshake.common.constant.PageLimits;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillRelease;
import io.github.linpeilie.annotations.AutoMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Skill 管理领域模型。
 *
 * @author wshake
 */
public final class SkillManageModels {

    private SkillManageModels() {}

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_DEPRECATED = "DEPRECATED";
    public static final String VISIBILITY_MARKET = "MARKET";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    public record SkillDraftListQuery(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {

        public static SkillDraftListQuery of(
                Integer page, Integer pageSize, Long ownerUserId, String name, String visibility, String status) {
            return new SkillDraftListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    ownerUserId,
                    trimToNull(name),
                    normalizeEnum(visibility),
                    normalizeEnum(status));
        }
    }

    public record CreateSkillDraftCommand(
            String name,
            String skillContent,
            String visibility,
            Long ownerUserId,
            Long basedOnReleaseId,
            String remark,
            Integer isEnabled) {}

    public record UpdateSkillDraftCommand(
            Long id,
            String name,
            String skillContent,
            String visibility,
            Long ownerUserId,
            String remark,
            Integer isEnabled) {}

    @AutoMapper(target = AgentSkillDraft.class)
    public record SkillDraftView(
            Long id,
            String name,
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
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record SkillResourceCommand(String resourcePath, String content) {}

    public record SkillDraftResourceView(String resourcePath, String content) {

        public static SkillDraftResourceView from(AgentSkillDraftResource r) {
            return new SkillDraftResourceView(r.getResourcePath(), r.getContent());
        }
    }

    @AutoMapper(target = AgentSkillRelease.class)
    public record SkillReleaseView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            Integer version,
            String status,
            Long sourceDraftId,
            String skillContent,
            String contentHash,
            String remark,
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ReviewCommand(String action, String comment) {}

    public record GitSourceCommand(
            String scope,
            Long ownerUserId,
            String url,
            String ref,
            String subdirectory,
            String encryptedSecret,
            String remark,
            Integer isEnabled) {}

    public record GitPreviewResult(String commitSha, List<GitPackagePreview> packages) {}

    public record GitPackagePreview(String skillPath, String name, String description, String contentHash) {}

    public record GitSyncCommand(Long sourceId, String expectedCommitSha, List<String> skillPaths) {}

    public record GitSyncResult(
            String commitSha,
            int created,
            int updated,
            int unchanged,
            int conflict,
            int failed,
            List<GitSyncItem> items) {}

    public record GitSyncItem(String skillPath, String result, Long draftId) {}

    /**
     * 计算内容 hash：对 SKILL.md 与全部资源按 resource_path 字典序拼接后的规范化字节做 SHA-256。
     */
    public static String contentHash(String skillContent, List<SkillResourceCommand> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(normalizeBytes(skillContent));
            List<SkillResourceCommand> sorted = new ArrayList<>(resources == null ? List.of() : resources);
            sorted.sort(Comparator.comparing(SkillResourceCommand::resourcePath));
            for (SkillResourceCommand r : sorted) {
                digest.update(normalizeBytes(r.resourcePath()));
                digest.update(normalizeBytes(r.content()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String contentHashOf(String skillContent, List<String> resourcePaths, List<String> contents) {
        List<SkillResourceCommand> resources = new ArrayList<>();
        if (resourcePaths != null) {
            for (int i = 0; i < resourcePaths.size(); i++) {
                resources.add(
                        new SkillResourceCommand(resourcePaths.get(i), contents == null ? null : contents.get(i)));
            }
        }
        return contentHash(skillContent, resources);
    }

    private static byte[] normalizeBytes(String value) {
        String normalized = value == null ? "" : value;
        return normalized.getBytes(StandardCharsets.UTF_8);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeEnum(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static int normalize01(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 0 ? 0 : 1;
    }

    static void requireResourcePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw com.wshake.common.exception.BizException.of(
                    com.wshake.common.result.ResultCode.PARAM_INVALID, "resourcePath is required");
        }
        String p = path.trim();
        if (p.contains("..") || p.startsWith("/") || p.startsWith("\\") || p.contains("\\")) {
            throw com.wshake.common.exception.BizException.of(
                    com.wshake.common.result.ResultCode.PARAM_INVALID,
                    "resourcePath must be relative and must not contain .. / backslash");
        }
    }
}
