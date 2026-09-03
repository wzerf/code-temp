package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 Git Skill 来源请求（字段 null 表示不改）。
 *
 * @author wshake
 */
@Data
@Schema(description = "更新 Git Skill 来源")
public class UpdateGitSourceRequest {

    @Size(max = 128)
    @Schema(description = "分支/标签/commit")
    private String ref;

    @Size(max = 255)
    @Schema(description = "仓库子目录")
    private String subdirectory;

    @Schema(description = "私有仓库密钥明文(传 null 表示不改)")
    private String plainSecret;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
