package com.wshake.service.agent.mcp;

import com.wshake.common.constant.PageLimits;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCP 管理领域模型。
 *
 * @author wshake
 */
public final class McpManageModels {

    private McpManageModels() {}

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_DEPRECATED = "DEPRECATED";
    public static final String VISIBILITY_MARKET = "MARKET";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    public record McpDraftListQuery(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {

        public static McpDraftListQuery of(
                Integer page, Integer pageSize, Long ownerUserId, String name, String visibility, String status) {
            return new McpDraftListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    ownerUserId,
                    trimToNull(name),
                    normalizeEnum(visibility),
                    normalizeEnum(status));
        }
    }

    public record CreateMcpDraftCommand(
            String name,
            String transport,
            String url,
            Map<String, String> headers,
            String encryptedSecret,
            Integer connectTimeoutMs,
            String visibility,
            Long ownerUserId,
            String remark,
            Integer isEnabled) {}

    public record UpdateMcpDraftCommand(
            Long id,
            String name,
            String transport,
            String url,
            Map<String, String> headers,
            String encryptedSecret,
            Integer connectTimeoutMs,
            String visibility,
            Long ownerUserId,
            String remark,
            Integer isEnabled) {}

    public record McpDraftView(
            Long id,
            String name,
            String transport,
            String url,
            Map<String, String> headers,
            String encryptedSecret,
            Integer connectTimeoutMs,
            String visibility,
            String status,
            Long ownerUserId,
            String reviewComment,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            String remark,
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record McpReleaseView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            Integer version,
            String status,
            Long sourceDraftId,
            String transport,
            String url,
            Map<String, String> headers,
            String encryptedSecret,
            Integer connectTimeoutMs,
            String remark,
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ReviewCommand(String action, String comment) {}

    public record ProbeResult(List<McpToolEntry> tools) {}

    public record McpToolEntry(String name, String description, Map<String, Object> inputSchema, boolean readOnly) {}

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
}
