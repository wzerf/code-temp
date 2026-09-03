package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定 Skill Release 到 Agent 会话请求（用户侧追加/覆盖）。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定 Skill 到会话")
public class BindSessionSkillRequest {

    @NotNull
    @Schema(description = "Skill Release id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long skillReleaseId;

    @NotNull
    @Schema(description = "skill_name(Session 内唯一;同名覆盖 Revision)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skillName;

    @Schema(description = "content_hash(运行漂移校验;缺省由服务端回填)")
    private String contentHash;
}
