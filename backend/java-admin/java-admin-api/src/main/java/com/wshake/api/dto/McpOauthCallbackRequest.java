package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MCP OAuth 回调换票请求（前端从 redirect_uri 取 code/state 后转交后端）。
 *
 * @author wshake
 */
@Data
@Schema(description = "MCP OAuth 回调换票")
public class McpOauthCallbackRequest {

    @NotBlank
    @Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank
    @Schema(description = "一次性 state", requiredMode = Schema.RequiredMode.REQUIRED)
    private String state;
}
