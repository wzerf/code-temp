package com.wshake.service.port;

import java.util.Map;

/**
 * MCP OAuth 出站端口（service → infra 的 AS 交互适配）。
 *
 * <p>业务层（McpOauthService）只依赖本接口做 DCR 动态注册、授权码/刷新换票、
 * AS metadata 发现，不耦合 OkHttp。token 明文只在内存短暂存在。
 *
 * @author wshake
 */
public interface McpOauthPort {

    /** 取 AS metadata（issuer 同源 .well-known；失败返回 null）。 */
    Map<String, String> fetchAuthorizationServerMetadata(String issuerOrigin);

    /**
     * DCR 动态客户端注册（RFC7591）。
     *
     * @param registrationEndpoint 注册端点（HTTPS）
     * @param redirectUri 本次登录回调地址
     * @return 注册到的 client_id
     */
    String dynamicRegister(String registrationEndpoint, String redirectUri);

    /**
     * token 端点换票（authorization_code / refresh_token）。
     *
     * @param tokenEndpoint 换票端点（HTTPS）
     * @param form 表单参数（grant_type/code/refresh_token/client_id/client_secret/code_verifier/redirect_uri）
     * @return 换票结果
     */
    TokenResponse exchangeToken(String tokenEndpoint, Map<String, String> form);

    /** 换票结果（明文只在内存）。 */
    record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, String scope) {}
}
