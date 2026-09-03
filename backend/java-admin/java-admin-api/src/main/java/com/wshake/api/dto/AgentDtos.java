package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Agent 管理请求 DTO（创建/更新/修订/绑定）。
 *
 * @author wshake
 */
public final class AgentDtos {

    private AgentDtos() {}

    @Data
    @Schema(description = "创建 Agent")
    public static class CreateAgentRequest {
        @NotBlank
        @Size(max = 64)
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Size(max = 512)
        @Schema(description = "描述")
        private String description;

        @Schema(description = "所有者（0=系统）")
        private Long ownerUserId;

        @Size(max = 512)
        private String remark;

        @Schema(description = "1=启用 0=禁用", example = "1")
        private Integer isEnabled;
    }

    @Data
    @Schema(description = "更新 Agent")
    public static class UpdateAgentRequest {
        @Size(max = 64)
        private String name;

        @Size(max = 512)
        private String description;

        private Long ownerUserId;

        @Size(max = 512)
        private String remark;

        private Integer isEnabled;
    }

    @Data
    @Schema(description = "创建草稿 Revision")
    public static class CreateRevisionRequest {
        @Schema(description = "系统提示词")
        private String systemPrompt;

        @Schema(description = "模型配置 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
        private Map<String, Object> modelConfig;

        @Schema(description = "权限策略 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
        private Map<String, Object> permissionPolicy;

        @Schema(description = "记忆策略 JSON")
        private Map<String, Object> memoryPolicy;

        @Schema(description = "压缩策略 JSON")
        private Map<String, Object> compressionPolicy;

        @Size(max = 512)
        private String remark;
    }

    @Data
    @Schema(description = "更新草稿 Revision")
    public static class UpdateRevisionRequest {
        private String systemPrompt;
        private Map<String, Object> modelConfig;
        private Map<String, Object> permissionPolicy;
        private Map<String, Object> memoryPolicy;
        private Map<String, Object> compressionPolicy;
        private String remark;
    }

    @Data
    @Schema(description = "回滚请求")
    public static class RollbackRequest {
        @Schema(description = "目标 PUBLISHED Revision ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long targetRevisionId;
    }

    @Data
    @Schema(description = "Skill 绑定项")
    public static class SkillBindingRequest {
        @Schema(description = "Release ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long skillReleaseId;

        @Schema(description = "Skill 名", requiredMode = Schema.RequiredMode.REQUIRED)
        private String skillName;

        private String contentHash;

        @Schema(description = "同名冲突胜者标记")
        private Integer overrideWinner;
    }

    @Data
    @Schema(description = "MCP 绑定项")
    public static class McpBindingRequest {
        @Schema(description = "Release ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long mcpReleaseId;

        @Schema(description = "server 名", requiredMode = Schema.RequiredMode.REQUIRED)
        private String mcpName;

        @Schema(description = "补配/覆盖的加密密钥")
        private String encryptedSecret;
    }

    @Data
    @Schema(description = "设置 Revision 绑定")
    public static class SetBindingsRequest {
        private List<SkillBindingRequest> skills;
        private List<McpBindingRequest> mcps;
    }
}
