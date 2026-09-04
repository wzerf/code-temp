package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会话记住模型选择请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定会话模型")
public class BindSessionModelRequest {

    @NotNull
    @Schema(description = "模型 Release id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long modelReleaseId;
}
