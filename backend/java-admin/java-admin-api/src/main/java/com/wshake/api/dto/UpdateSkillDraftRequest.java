package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 更新 Skill 草稿请求（字段 null 表示不改）。
 *
 * @author wshake
 */
@Data
@Schema(description = "更新 Skill 草稿")
public class UpdateSkillDraftRequest {

    @Size(max = 128)
    @Schema(description = "Skill 名")
    private String name;

    @Size(max = 512)
    @Schema(description = "描述")
    private String description;

    @Schema(description = "完整 SKILL.md 全文")
    private String skillContent;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;

    @Schema(description = "资源文件列表(null=不改)")
    private List<CreateSkillDraftRequest.ResourceInputRequest> resources;
}
