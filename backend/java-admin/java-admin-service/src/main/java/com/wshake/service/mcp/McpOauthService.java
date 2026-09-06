package com.wshake.service.mcp;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentMcpOauthState;
import com.wshake.service.entity.AgentMcpOauthToken;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.port.McpOauthPort;
import com.wshake.service.port.McpOauthPort.TokenResponse;
import com.wshake.service.repository.AgentMcpOauthStateRepository;
import com.wshake.service.repository.AgentMcpOauthTokenRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP OAuth 登录服务：Authorization Code + PKCE 完整闭环。
 *
 * <p>流程：start（DCR 可选 + 拼授权地址 + 存一次性 state）→ 用户浏览器登录 →
 * callback（校验 state + code 换票 + 落库）→ status/refresh/revoke。
 * token 按 (release, user) 隔离；access/refresh 密文落库，明文只在内存。
 * 出站 HTTP 经 {@link McpOauthPort}，本类只做编排。
 *
 * <p>安全红线：仅 HTTPS 回调与端点（本地 http://localhost 除外）；
 * client_secret/token 明文不进日志；state 一次性 10 分钟过期。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class McpOauthService {

    private static final int STATE_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentMcpReleaseRepository releaseRepository;
    private final AgentMcpOauthStateRepository stateRepository;
    private final AgentMcpOauthTokenRepository tokenRepository;
    private final AgentSecretCipher secretCipher;
    private final McpOauthPort oauthPort;

    /** 发起登录：返回浏览器应打开的授权地址。 */
    @Transactional
    public StartLoginResult startLogin(Long releaseId, Long userId, String redirectUri) {
        AgentMcpRelease release = requireOauthRelease(releaseId);
        requireHttpsUrl(redirectUri, true);
        String authorizationEndpoint = release.getOauthAuthorizationEndpoint();
        if (authorizationEndpoint == null || authorizationEndpoint.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该 MCP 未发现授权端点，无法登录");
        }
        requireHttpsUrl(authorizationEndpoint, false);

        String tokenEndpoint = release.getOauthTokenEndpoint();
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            tokenEndpoint = discoverTokenEndpoint(authorizationEndpoint);
        }

        // client：配置优先，否则 DCR 动态注册
        String clientId = release.getOauthClientId();
        if (clientId == null || clientId.isBlank()) {
            String registrationEndpoint = discoverRegistrationEndpoint(tokenEndpoint);
            if (registrationEndpoint == null || registrationEndpoint.isBlank()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "该 MCP 未配置 Client ID 且不支持动态注册，请联系发布者配置");
            }
            requireHttpsUrl(registrationEndpoint, false);
            clientId = oauthPort.dynamicRegister(registrationEndpoint, redirectUri);
        }

        String scope =
                release.getOauthScope() == null ? "" : release.getOauthScope().trim();
        String resource = release.getUrl() == null ? "" : release.getUrl().trim();
        String verifier = randomString(64);
        String state = randomString(32);
        String challenge = pkceChallenge(verifier);

        AgentMcpOauthState row = new AgentMcpOauthState();
        row.setState(state);
        row.setMcpReleaseId(releaseId);
        row.setUserId(userId);
        row.setCodeVerifier(verifier);
        row.setRedirectUri(redirectUri);
        row.setScope(scope);
        row.setResource(resource);
        row.setClientId(clientId);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(STATE_MINUTES));
        stateRepository.insert(row);
        stateRepository.deleteExpired(LocalDateTime.now());

        String url = authorizationEndpoint + (authorizationEndpoint.contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + (scope.isEmpty() ? "" : "&scope=" + encode(scope))
                + (resource.isEmpty() ? "" : "&resource=" + encode(resource))
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
        return new StartLoginResult(url, state);
    }

    /** 回调换票：校验 state → code 换 access/refresh → 落库。 */
    @Transactional
    public void callback(String code, String state, Long userId) {
        if (code == null || code.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        AgentMcpOauthState saved = stateRepository.findValidByState(state, now);
        if (saved == null || !saved.getUserId().equals(userId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "state 无效或已过期，请重新发起登录");
        }
        stateRepository.deleteById(saved.getId());

        AgentMcpRelease release = requireOauthRelease(saved.getMcpReleaseId());
        String tokenEndpoint = release.getOauthTokenEndpoint();
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            tokenEndpoint = discoverTokenEndpoint(release.getOauthAuthorizationEndpoint());
        }
        requireHttpsUrl(tokenEndpoint, false);
        String clientSecret = secretCipher.decrypt(release.getOauthClientSecretEnc());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", saved.getRedirectUri());
        form.put("client_id", saved.getClientId());
        form.put("code_verifier", saved.getCodeVerifier());
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.put("client_secret", clientSecret);
        }
        TokenResponse token = oauthPort.exchangeToken(tokenEndpoint, form);
        saveToken(saved.getMcpReleaseId(), userId, token);
    }

    /** 当前用户登录态（供前端展示徽标/有效期）。 */
    public TokenStatus status(Long releaseId, Long userId) {
        AgentMcpOauthToken token = tokenRepository.findByReleaseAndUser(releaseId, userId);
        if (token == null) {
            return new TokenStatus(false, false, null, null);
        }
        boolean expired = token.getExpiresAt() != null && !token.getExpiresAt().isAfter(LocalDateTime.now());
        return new TokenStatus(true, expired, token.getExpiresAt(), token.getScope());
    }

    /** 刷新 token（refresh_token 换票）。 */
    @Transactional
    public TokenStatus refresh(Long releaseId, Long userId) {
        AgentMcpRelease release = requireOauthRelease(releaseId);
        AgentMcpOauthToken saved = tokenRepository.findByReleaseAndUser(releaseId, userId);
        if (saved == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "尚未登录，请先完成 OAuth 登录");
        }
        String refreshToken = secretCipher.decrypt(saved.getRefreshTokenEnc());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "无 refresh_token，请重新登录");
        }
        String tokenEndpoint = release.getOauthTokenEndpoint();
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            tokenEndpoint = discoverTokenEndpoint(release.getOauthAuthorizationEndpoint());
        }
        requireHttpsUrl(tokenEndpoint, false);
        String clientSecret = secretCipher.decrypt(release.getOauthClientSecretEnc());
        String clientId = release.getOauthClientId();
        if (clientId == null || clientId.isBlank()) {
            clientId = "unknown";
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.put("client_secret", clientSecret);
        }
        TokenResponse token = oauthPort.exchangeToken(tokenEndpoint, form);
        // 部分 AS 刷新时不返回新 refresh_token：沿用旧值
        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            token = new TokenResponse(
                    token.accessToken(), refreshToken, token.tokenType(), token.expiresIn(), token.scope());
        }
        saveToken(releaseId, userId, token);
        return status(releaseId, userId);
    }

    /** 解绑授权（删除当前用户 token）。 */
    @Transactional
    public void revoke(Long releaseId, Long userId) {
        tokenRepository.deleteByReleaseAndUser(releaseId, userId);
    }

    /** 取当前用户可用 access_token 明文（运行时注入请求头；null=未登录）。 */
    public String accessTokenFor(Long releaseId, Long userId) {
        AgentMcpOauthToken saved = tokenRepository.findByReleaseAndUser(releaseId, userId);
        if (saved == null) {
            return null;
        }
        if (saved.getExpiresAt() != null && !saved.getExpiresAt().isAfter(LocalDateTime.now())) {
            return null;
        }
        return secretCipher.decrypt(saved.getAccessTokenEnc());
    }

    // ---------- 内部 ----------

    private void saveToken(Long releaseId, Long userId, TokenResponse token) {
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "换票失败：未返回 access_token");
        }
        AgentMcpOauthToken row = new AgentMcpOauthToken();
        row.setMcpReleaseId(releaseId);
        row.setUserId(userId);
        row.setAccessTokenEnc(secretCipher.encrypt(token.accessToken()));
        row.setRefreshTokenEnc(secretCipher.encrypt(token.refreshToken()));
        row.setTokenType(token.tokenType() == null || token.tokenType().isBlank() ? "Bearer" : token.tokenType());
        if (token.expiresIn() > 0) {
            row.setExpiresAt(LocalDateTime.now().plusSeconds(token.expiresIn()));
        }
        row.setScope(token.scope() == null ? "" : token.scope());
        tokenRepository.upsert(row);
    }

    private String discoverTokenEndpoint(String authorizationEndpoint) {
        try {
            URI uri = URI.create(authorizationEndpoint);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            Map<String, String> metadata = oauthPort.fetchAuthorizationServerMetadata(origin);
            if (metadata != null
                    && metadata.get("token_endpoint") != null
                    && !metadata.get("token_endpoint").isBlank()) {
                return metadata.get("token_endpoint");
            }
        } catch (Exception e) {
            // fallthrough
        }
        throw BizException.of(ResultCode.PARAM_INVALID, "无法发现 token_endpoint，请检查授权服务metadata");
    }

    private String discoverRegistrationEndpoint(String tokenEndpoint) {
        try {
            URI uri = URI.create(tokenEndpoint);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            Map<String, String> metadata = oauthPort.fetchAuthorizationServerMetadata(origin);
            if (metadata != null) {
                return metadata.getOrDefault("registration_endpoint", "");
            }
        } catch (Exception e) {
            // fallthrough
        }
        return "";
    }

    AgentMcpRelease requireOauthRelease(Long releaseId) {
        if (releaseId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "releaseId 不能为空");
        }
        AgentMcpRelease release = releaseRepository.findById(releaseId);
        if (release == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "mcp release " + releaseId + " not found");
        }
        if (!"OAUTH".equalsIgnoreCase(release.getAuthType())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该 MCP 不是 OAuth 类型，无需登录");
        }
        return release;
    }

    static void requireHttpsUrl(String url, boolean allowLocalhostHttp) {
        if (url == null || url.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "URL 不能为空");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if ("https".equals(scheme)) {
                return;
            }
            if (allowLocalhostHttp && "http".equals(scheme) && ("localhost".equals(host) || "127.0.0.1".equals(host))) {
                return;
            }
            throw BizException.of(ResultCode.PARAM_INVALID, "OAuth 相关 URL 必须为 HTTPS：" + url);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "URL 非法：" + url);
        }
    }

    static String pkceChallenge(String verifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("PKCE 计算失败", e);
        }
    }

    private static String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record StartLoginResult(String authorizationUrl, String state) {}

    public record TokenStatus(boolean loggedIn, boolean expired, LocalDateTime expiresAt, String scope) {}
}
