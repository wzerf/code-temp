package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 创建 Skill 草稿请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "创建 Skill 草稿")
public class CreateSkillDraftRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Skill 名(来自 SKILL.md frontmatter name)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 512)
    @Schema(description = "描述(来自 SKILL.md frontmatter description)")
    private String description;

    @Schema(description = "完整 SKILL.md 全文")
    private String skillContent;

    @NotBlank
    @Schema(description = "MARKET=进市场 / PRIVATE=仅所有者", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRIVATE")
    private String visibility;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "资源文件列表(相对路径;禁止 .. / 绝对路径 / 反斜杠)")
    private List<ResourceInputRequest> resources;

    /** 资源文件条目。 */
    @Data
    @Schema(description = "Skill 资源文件")
    public static class ResourceInputRequest {
        @NotBlank
        @Schema(description = "相对路径", example = "references/usage.md")
        private String resourcePath;

        @Schema(description = "文件内容")
        private String content;
    }
}
