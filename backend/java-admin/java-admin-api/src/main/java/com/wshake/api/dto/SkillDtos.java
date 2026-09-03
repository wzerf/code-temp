package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * Skill 管理请求 DTO。
 *
 * @author wshake
 */
public final class SkillDtos {

    private SkillDtos() {}

    @Data
    @Schema(description = "创建 Skill 草稿")
    public static class CreateSkillDraftRequest {
        @NotBlank
        @Size(max = 64)
        @Schema(description = "Skill 名", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "完整 SKILL.md")
        private String skillContent;

        @Schema(description = "MARKET / PRIVATE", example = "PRIVATE")
        private String visibility;

        @Schema(description = "所有者（0=系统）")
        private Long ownerUserId;

        @Schema(description = "从既有 Release 开草稿时的来源")
        private Long basedOnReleaseId;

        @Size(max = 512)
        private String remark;

        private Integer isEnabled;
    }

    @Data
    @Schema(description = "更新 Skill 草稿")
    public static class UpdateSkillDraftRequest {
        @Size(max = 64)
        private String name;

        private String skillContent;
        private String visibility;
        private Long ownerUserId;
        private String remark;
        private Integer isEnabled;
    }

    @Data
    @Schema(description = "Skill 资源项")
    public static class SkillResourceRequest {
        @NotBlank
        private String resourcePath;

        private String content;
    }

    @Data
    @Schema(description = "设置 Skill 草稿资源")
    public static class SetSkillResourcesRequest {
        private List<SkillResourceRequest> resources;
    }

    @Data
    @Schema(description = "审核 Skill 草稿")
    public static class ReviewRequest {
        @Schema(description = "approve | reject", requiredMode = Schema.RequiredMode.REQUIRED)
        private String action;

        @Size(max = 512)
        private String comment;
    }

    @Data
    @Schema(description = "Git 来源配置")
    public static class GitSourceRequest {
        @Schema(description = "MARKET / PRIVATE", requiredMode = Schema.RequiredMode.REQUIRED)
        private String scope;

        private Long ownerUserId;

        @NotBlank
        @Schema(description = "HTTPS 地址", requiredMode = Schema.RequiredMode.REQUIRED)
        private String url;

        @Schema(description = "分支/标签/commit", example = "main")
        private String ref;

        private String subdirectory;
        private String encryptedSecret;
        private String remark;
        private Integer isEnabled;
    }

    @Data
    @Schema(description = "Git 同步请求")
    public static class GitSyncRequest {
        @Schema(description = "来源 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long sourceId;

        @Schema(description = "期望 commit SHA")
        private String expectedCommitSha;

        @Schema(description = "要导入的包路径列表")
        private List<String> skillPaths;
    }
}
