package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发起 MCP OAuth 登录请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "发起 MCP OAuth 登录")
public class StartMcpOauthRequest {

    @NotBlank
    @Schema(description = "OAuth 回调地址（必须 HTTPS；本地允许 http://localhost）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String redirectUri;
}
