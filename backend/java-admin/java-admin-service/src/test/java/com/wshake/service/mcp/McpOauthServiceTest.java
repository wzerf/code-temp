package com.wshake.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSecretProperties;
import com.wshake.service.entity.AgentMcpOauthState;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.port.McpOauthPort;
import com.wshake.service.port.McpOauthPort.TokenResponse;
import com.wshake.service.repository.AgentMcpOauthStateRepository;
import com.wshake.service.repository.AgentMcpOauthTokenRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link McpOauthService} 登录闭环：start 拼地址、callback 换票、status/refresh/revoke。
 */
class McpOauthServiceTest {

    private final AgentMcpReleaseRepository releaseRepo = mock(AgentMcpReleaseRepository.class);
    private final AgentMcpOauthStateRepository stateRepo = mock(AgentMcpOauthStateRepository.class);
    private final AgentMcpOauthTokenRepository tokenRepo = mock(AgentMcpOauthTokenRepository.class);
    private final McpOauthPort oauthPort = mock(McpOauthPort.class);

    private AgentSecretCipher cipher;
    private McpOauthService service;

    @BeforeEach
    void init() {
        AgentSecretProperties props = new AgentSecretProperties();
        cipher = new AgentSecretCipher(props);
        cipher.init();
        service = new McpOauthService(releaseRepo, stateRepo, tokenRepo, cipher, oauthPort);
    }

    private AgentMcpRelease oauthRelease() {
        AgentMcpRelease r = new AgentMcpRelease();
        r.setId(50L);
        r.setName("github");
        r.setVisibility("MARKET");
        r.setAuthType("OAUTH");
        r.setOauthClientId("client-123");
        r.setOauthScope("read");
        r.setOauthAuthorizationEndpoint("https://auth.example.com/authorize");
        r.setOauthTokenEndpoint("https://auth.example.com/token");
        r.setUrl("https://mcp.example.com/mcp");
        return r;
    }

    @Test
    void startLogin_buildsAuthorizeUrlWithPkce() {
        when(releaseRepo.findById(50L)).thenReturn(oauthRelease());

        var result = service.startLogin(50L, 7L, "https://app.example.com/mcp/oauth/callback");

        assertThat(result.authorizationUrl()).startsWith("https://auth.example.com/authorize?");
        assertThat(result.authorizationUrl()).contains("response_type=code");
        assertThat(result.authorizationUrl()).contains("client_id=client-123");
        assertThat(result.authorizationUrl()).contains("code_challenge=");
        assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
        assertThat(result.state()).isNotBlank();
        ArgumentCaptor<AgentMcpOauthState> captor = ArgumentCaptor.forClass(AgentMcpOauthState.class);
        verify(stateRepo).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getCodeVerifier()).isNotBlank();
        assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void startLogin_rejectsNonOauthRelease() {
        AgentMcpRelease r = oauthRelease();
        r.setAuthType("NONE");
        when(releaseRepo.findById(50L)).thenReturn(r);
        assertThatThrownBy(() -> service.startLogin(50L, 7L, "https://app.example.com/cb"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是 OAuth 类型");
    }

    @Test
    void startLogin_rejectsHttpRedirect() {
        when(releaseRepo.findById(50L)).thenReturn(oauthRelease());
        assertThatThrownBy(() -> service.startLogin(50L, 7L, "http://evil.example.com/cb"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void startLogin_allowsLocalhostHttpRedirect() {
        when(releaseRepo.findById(50L)).thenReturn(oauthRelease());
        var result = service.startLogin(50L, 7L, "http://localhost:7000/mcp/oauth/callback");
        assertThat(result.authorizationUrl()).contains("redirect_uri=");
    }

    @Test
    void callback_exchangesCodeAndUpsertsToken() {
        AgentMcpOauthState saved = new AgentMcpOauthState();
        saved.setId(9L);
        saved.setState("s");
        saved.setMcpReleaseId(50L);
        saved.setUserId(7L);
        saved.setCodeVerifier("verifier");
        saved.setRedirectUri("https://app.example.com/cb");
        saved.setClientId("client-123");
        when(stateRepo.findValidByState(any(), any())).thenReturn(saved);
        when(releaseRepo.findById(50L)).thenReturn(oauthRelease());
        when(oauthPort.exchangeToken(any(), any()))
                .thenReturn(new TokenResponse("at-1", "rt-1", "Bearer", 3600, "read"));

        service.callback("code-1", "s", 7L);

        verify(stateRepo).deleteById(9L);
        verify(tokenRepo).upsert(any());
    }

    @Test
    void callback_rejectsWrongUser() {
        AgentMcpOauthState saved = new AgentMcpOauthState();
        saved.setId(9L);
        saved.setUserId(8L);
        when(stateRepo.findValidByState(any(), any())).thenReturn(saved);
        assertThatThrownBy(() -> service.callback("code-1", "s", 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("state");
    }

    @Test
    void status_notLoggedIn() {
        when(tokenRepo.findByReleaseAndUser(50L, 7L)).thenReturn(null);
        var status = service.status(50L, 7L);
        assertThat(status.loggedIn()).isFalse();
    }

    @Test
    void revoke_deletesToken() {
        service.revoke(50L, 7L);
        verify(tokenRepo).deleteByReleaseAndUser(50L, 7L);
    }

    @Test
    void pkceChallenge_isS256() {
        // RFC7636 附录 B 向量
        assertThat(McpOauthService.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void requireHttpsUrl_rejectsPlainHttp() {
        assertThatThrownBy(() -> McpOauthService.requireHttpsUrl("http://auth.example.com/x", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTPS");
    }
}
