package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP OAuth 登录相关 VO。
 *
 * @author wshake
 */
public final class McpOauthVO {

    private McpOauthVO() {}

    /** 发起登录返回（浏览器打开授权地址）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "MCP OAuth 登录地址")
    public static class StartLoginVO {
        @Schema(description = "浏览器应打开的授权地址")
        private String authorizationUrl;

        @Schema(description = "一次性 state（回跳校验用）")
        private String state;
    }

    /** 当前用户登录态。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "MCP OAuth 登录态")
    public static class TokenStatusVO {
        @Schema(description = "是否已登录")
        private Boolean loggedIn;

        @Schema(description = "token 是否已过期")
        private Boolean expired;

        @Schema(description = "过期时间（服务端未给则空）")
        private LocalDateTime expiresAt;

        @Schema(description = "实际授予 scope")
        private String scope;
    }
}
