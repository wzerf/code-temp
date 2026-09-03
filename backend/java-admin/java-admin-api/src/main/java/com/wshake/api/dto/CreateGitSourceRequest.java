package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Git Skill 来源请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "创建 Git Skill 来源")
public class CreateGitSourceRequest {

    @NotBlank
    @Schema(
            description = "MARKET(仅管理员)/PRIVATE(归当前用户)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "PRIVATE")
    private String scope;

    @Schema(description = "来源所有者(0=平台/当前用户)")
    private Long ownerUserId;

    @NotBlank
    @Size(max = 255)
    @Schema(
            description = "HTTPS 地址(禁 SSH/本地路径/user-info)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "https://github.com/org/skills-repo.git")
    private String url;

    @Size(max = 128)
    @Schema(description = "分支/标签/commit,默认 HEAD")
    private String ref;

    @Size(max = 255)
    @Schema(description = "仓库子目录(空=根目录)")
    private String subdirectory;

    @Schema(description = "私有仓库密钥明文(落库加密;空=匿名 clone)")
    private String plainSecret;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
