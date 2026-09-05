package com.wshake.service.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSecretProperties;
import com.wshake.service.entity.AgentMcpDraft;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.mcp.McpControlService.CreateMcpCommand;
import com.wshake.service.port.McpProbePort;
import com.wshake.service.port.McpProbePort.McpToolEntry;
import com.wshake.service.port.McpProbePort.OAuthChallenge;
import com.wshake.service.port.McpProbePort.ProbeCommand;
import com.wshake.service.port.McpProbePort.ProbeResult;
import com.wshake.service.repository.AgentMcpDraftRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link McpControlService} 状态机、密钥加解密与握手语义。
 */
class McpControlServiceTest {

    private final AgentMcpDraftRepository draftRepo = mock(AgentMcpDraftRepository.class);
    private final AgentMcpReleaseRepository releaseRepo = mock(AgentMcpReleaseRepository.class);
    private final McpProbePort probePort = mock(McpProbePort.class);

    private AgentSecretCipher cipher;
    private McpControlService service;

    @BeforeEach
    void init() {
        AgentSecretProperties props = new AgentSecretProperties();
        cipher = new AgentSecretCipher(props);
        cipher.init();
        service = new McpControlService(
                draftRepo, releaseRepo, cipher, probePort, new io.github.linpeilie.Converter(), new ObjectMapper());
    }

    private AgentMcpDraft draft(Long id, String status, String visibility, String secretCipher) {
        AgentMcpDraft d = new AgentMcpDraft();
        d.setId(id);
        d.setOwnerUserId(1L);
        d.setName("github");
        d.setVisibility(visibility);
        d.setStatus(status);
        d.setTransport("http");
        d.setUrl("https://mcp.example.com/mcp");
        d.setEncryptedSecret(secretCipher);
        d.setConnectTimeoutMs(5000);
        d.setIsEnabled(1);
        return d;
    }

    @Test
    void getDraft_echoesEditableFieldsIncludingTimeout() {
        AgentMcpDraft d = draft(10L, "DRAFT", "PRIVATE", cipher.encrypt("sk"));
        d.setHeadersJson("{\"Accept\":\"application/json\"}");
        d.setConnectTimeoutMs(8000);
        d.setRemark("备注");
        when(draftRepo.findById(10L)).thenReturn(d);

        var view = service.getDraft(10L);

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.name()).isEqualTo("github");
        assertThat(view.transport()).isEqualTo("http");
        assertThat(view.url()).isEqualTo("https://mcp.example.com/mcp");
        assertThat(view.headersJson()).isEqualTo("{\"Accept\":\"application/json\"}");
        assertThat(view.connectTimeoutMs()).isEqualTo(8000);
        assertThat(view.remark()).isEqualTo("备注");
        assertThat(view.visibility()).isEqualTo("PRIVATE");
    }

    @Test
    void createPrivateDraft_encryptsSecret() {
        when(draftRepo.existsActiveDraft(1L, "github", "PRIVATE", null)).thenReturn(false);
        doAnswer(inv -> {
                    inv.getArgument(0, AgentMcpDraft.class).setId(10L);
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentMcpDraft.class));
        AgentMcpDraft saved = draft(10L, "DRAFT", "PRIVATE", null);
        when(draftRepo.findById(10L)).thenReturn(saved);

        CreateMcpCommand cmd = new CreateMcpCommand(
                "github", "http", "https://mcp.example.com/mcp", null, "PRIVATE", "sk-secret-123", 5000, "", 1L);
        var view = service.createDraft(cmd);

        assertThat(view.id()).isEqualTo(10L);
        // 捕获实际 insert 的行断言密文
        org.mockito.ArgumentCaptor<AgentMcpDraft> captor = org.mockito.ArgumentCaptor.forClass(AgentMcpDraft.class);
        verify(draftRepo).insert(captor.capture());
        String stored = captor.getValue().getEncryptedSecret();
        assertThat(stored).isNotBlank();
        assertThat(stored).isNotEqualTo("sk-secret-123");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-secret-123");
    }

    @Test
    void createMarketDraft_ignoresSecret() {
        when(draftRepo.existsActiveDraft(1L, "github", "MARKET", null)).thenReturn(false);
        doAnswer(inv -> {
                    inv.getArgument(0, AgentMcpDraft.class).setId(11L);
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentMcpDraft.class));
        AgentMcpDraft saved = draft(11L, "DRAFT", "MARKET", null);
        when(draftRepo.findById(11L)).thenReturn(saved);

        CreateMcpCommand cmd = new CreateMcpCommand(
                "github", "http", "https://mcp.example.com/mcp", null, "MARKET", "should-not-store", 5000, "", 1L);
        service.createDraft(cmd);

        org.mockito.ArgumentCaptor<AgentMcpDraft> captor = org.mockito.ArgumentCaptor.forClass(AgentMcpDraft.class);
        verify(draftRepo).insert(captor.capture());
        assertThat(captor.getValue().getEncryptedSecret()).isNullOrEmpty();
    }

    @Test
    void createDraft_nullHeaders_shouldStoreNullNotBlank() {
        when(draftRepo.existsActiveDraft(1L, "firecrawl", "MARKET", null)).thenReturn(false);
        doAnswer(inv -> {
                    inv.getArgument(0, AgentMcpDraft.class).setId(12L);
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentMcpDraft.class));
        AgentMcpDraft saved = draft(12L, "DRAFT", "MARKET", null);
        when(draftRepo.findById(12L)).thenReturn(saved);

        // headersJson=null + visibility=MARKET 是 firecrawl 上架场景
        CreateMcpCommand cmd = new CreateMcpCommand(
                "firecrawl", "http", "https://api.firecrawl.dev/v2", null, "MARKET", null, 1000, null, 1L);
        service.createDraft(cmd);

        org.mockito.ArgumentCaptor<AgentMcpDraft> captor = org.mockito.ArgumentCaptor.forClass(AgentMcpDraft.class);
        verify(draftRepo).insert(captor.capture());
        // MySQL JSON 列禁止空串;无头必须为 null
        assertThat(captor.getValue().getHeadersJson()).isNull();
        // MARKET 不落密钥
        assertThat(captor.getValue().getEncryptedSecret()).isNullOrEmpty();
    }

    @Test
    void approve_marketRelease_headersAndSecretSafe() {
        AgentMcpDraft d = draft(1L, "PENDING_REVIEW", "MARKET", null);
        d.setHeadersJson(""); // 存量脏数据:空串
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(ProbeResult.tools(List.of(new McpToolEntry("tool_a", "", "", false))));
        when(releaseRepo.listByNameAllVersions(1L, "MARKET", "github")).thenReturn(List.of());
        doAnswer(inv -> {
                    inv.getArgument(0, AgentMcpRelease.class).setId(51L);
                    return 1;
                })
                .when(releaseRepo)
                .insert(any(AgentMcpRelease.class));
        AgentMcpRelease r = new AgentMcpRelease();
        r.setId(51L);
        r.setName("github");
        r.setVisibility("MARKET");
        r.setVersion(1);
        when(releaseRepo.findById(51L)).thenReturn(r);

        var view = service.approve(1L);

        org.mockito.ArgumentCaptor<AgentMcpRelease> captor = org.mockito.ArgumentCaptor.forClass(AgentMcpRelease.class);
        verify(releaseRepo).insert(captor.capture());
        assertThat(captor.getValue().getHeadersJson()).isNull();
        assertThat(captor.getValue().getEncryptedSecret()).isNull();
    }

    @Test
    void verify_probesAndReturnsTools() {
        AgentMcpDraft d = draft(1L, "DRAFT", "PRIVATE", cipher.encrypt("token-abc"));
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(ProbeResult.tools(List.of(new McpToolEntry("get_platform_time", "now", "", false))));

        var result = service.verify(1L);
        assertThat(result.success()).isTrue();
        assertThat(result.tools()).hasSize(1);
        assertThat(result.tools().get(0).name()).isEqualTo("get_platform_time");
        assertThat(result.oauthAuthorizationUrl()).isNull();
    }

    @Test
    void verify_oauthRequired_returnsAuthorizationUrl() {
        AgentMcpDraft d = draft(1L, "DRAFT", "PRIVATE", "");
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(ProbeResult.oauth(new OAuthChallenge(
                        "https://www.facebook.com/v26.0/dialog/oauth",
                        "developer_tools_mcp_app_read",
                        "https://mcp.facebook.com/devtools",
                        "https://mcp.facebook.com/.well-known/oauth-protected-resource/devtools")));

        var result = service.verify(1L);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("OAuth");
        assertThat(result.oauthAuthorizationUrl()).isEqualTo("https://www.facebook.com/v26.0/dialog/oauth");
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void verify_rejectsEmptyCatalog() {
        AgentMcpDraft d = draft(1L, "DRAFT", "PRIVATE", cipher.encrypt("token"));
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class))).thenReturn(ProbeResult.tools(List.of()));
        assertThatThrownBy(() -> service.verify(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工具目录为空");
    }

    @Test
    void approve_onlyFromPendingReviewAndInsertsRelease() {
        AgentMcpDraft d = draft(1L, "PENDING_REVIEW", "MARKET", "");
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(ProbeResult.tools(List.of(new McpToolEntry("tool_a", "", "", false))));
        when(releaseRepo.listByNameAllVersions(1L, "MARKET", "github")).thenReturn(List.of());
        doAnswer(inv -> {
                    inv.getArgument(0, AgentMcpRelease.class).setId(50L);
                    return 1;
                })
                .when(releaseRepo)
                .insert(any(AgentMcpRelease.class));
        AgentMcpRelease r = new AgentMcpRelease();
        r.setId(50L);
        r.setName("github");
        r.setVisibility("MARKET");
        r.setVersion(1);
        when(releaseRepo.findById(50L)).thenReturn(r);

        var view = service.approve(1L);

        assertThat(view.version()).isEqualTo(1);
        // MARKET Release 无密钥
        assertThat(view.hasSecret()).isFalse();
        verify(releaseRepo).insert(any(AgentMcpRelease.class));
        verify(draftRepo).updateStatus(1L, "CONSUMED", "", null, null);
    }

    @Test
    void approve_rejectsWhenProbeFails() {
        AgentMcpDraft d = draft(1L, "PENDING_REVIEW", "PRIVATE", cipher.encrypt("tok"));
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class))).thenThrow(new RuntimeException("connection refused"));
        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("握手失败");
        verify(releaseRepo, never()).insert(any());
    }

    @Test
    void approve_oauthRequired_rejectsWithoutInsert() {
        AgentMcpDraft d = draft(1L, "PENDING_REVIEW", "PRIVATE", "");
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(ProbeResult.oauth(
                        new OAuthChallenge("https://www.facebook.com/v26.0/dialog/oauth", "", "", "")));
        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("OAuth")
                .hasMessageContaining("https://www.facebook.com/v26.0/dialog/oauth");
        verify(releaseRepo, never()).insert(any());
    }
}
