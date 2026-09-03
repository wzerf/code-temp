package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * MCP 管理请求 DTO。
 *
 * @author wshake
 */
public final class McpDtos {

    private McpDtos() {}

    @Data
    @Schema(description = "创建 MCP 草稿")
    public static class CreateMcpDraftRequest {
        @NotBlank
        @Size(max = 64)
        @Schema(description = "server 名", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "sse / http", example = "sse")
        private String transport;

        @NotBlank
        @Size(max = 512)
        @Schema(description = "连接地址", requiredMode = Schema.RequiredMode.REQUIRED)
        private String url;

        @Schema(description = "静态头（无密）")
        private Map<String, String> headers;

        @Schema(description = "加密密钥密文（私有草稿可带）")
        private String encryptedSecret;

        @Schema(description = "连接超时（毫秒）", example = "5000")
        private Integer connectTimeoutMs;

        @Schema(description = "MARKET / PRIVATE", example = "PRIVATE")
        private String visibility;

        @Schema(description = "所有者（0=系统）")
        private Long ownerUserId;

        @Size(max = 512)
        private String remark;

        private Integer isEnabled;
    }

    @Data
    @Schema(description = "更新 MCP 草稿")
    public static class UpdateMcpDraftRequest {
        @Size(max = 64)
        private String name;

        private String transport;

        @Size(max = 512)
        private String url;

        private Map<String, String> headers;
        private String encryptedSecret;
        private Integer connectTimeoutMs;
        private String visibility;
        private Long ownerUserId;
        private String remark;
        private Integer isEnabled;
    }

    @Data
    @Schema(description = "审核 MCP 草稿")
    public static class ReviewRequest {
        @Schema(description = "approve | reject", requiredMode = Schema.RequiredMode.REQUIRED)
        private String action;

        @Size(max = 512)
        private String comment;
    }
}
