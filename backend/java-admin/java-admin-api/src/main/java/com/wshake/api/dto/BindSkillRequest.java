package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定 Skill Release 到 Agent Revision 请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定 Skill 到 Revision")
public class BindSkillRequest {

    @NotNull
    @Schema(description = "Skill Release id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skillReleaseId;

    @NotNull
    @Schema(description = "skill_name(从 Release 拷贝)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skillName;

    @Schema(description = "content_hash(运行漂移校验;缺省由服务端回填)")
    private String contentHash;

    @Schema(description = "同名覆盖胜者标记(0/1)")
    private Integer overrideWinner;
}
