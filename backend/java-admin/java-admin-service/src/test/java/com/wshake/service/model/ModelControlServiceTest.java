package com.wshake.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSecretProperties;
import com.wshake.service.entity.AgentModelDraft;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.model.ModelControlService.CreateModelCommand;
import com.wshake.service.port.ModelProbePort;
import com.wshake.service.port.ModelProbePort.ProbeCommand;
import com.wshake.service.port.ModelProbePort.ProbeResult;
import com.wshake.service.repository.AgentModelDraftRepository;
import com.wshake.service.repository.AgentModelReleaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ModelControlService} 状态机、密钥加解密、官方/私有分流与探测语义。
 */
class ModelControlServiceTest {

    private final AgentModelDraftRepository draftRepo = mock(AgentModelDraftRepository.class);
    private final AgentModelReleaseRepository releaseRepo = mock(AgentModelReleaseRepository.class);
    private final ModelProbePort probePort = mock(ModelProbePort.class);

    private AgentSecretCipher cipher;
    private ModelControlService service;

    @BeforeEach
    void init() {
        AgentSecretProperties props = new AgentSecretProperties();
        cipher = new AgentSecretCipher(props);
        cipher.init();
        service = new ModelControlService(
                draftRepo, releaseRepo, cipher, probePort, new io.github.linpeilie.Converter(), new ObjectMapper());
    }

    private AgentModelDraft draft(Long id, String status, String scope, String secretCipher) {
        AgentModelDraft d = new AgentModelDraft();
        d.setId(id);
        d.setOwnerUserId(1L);
        d.setName("gpt-4o");
        d.setScope(scope);
        d.setCode("");
        d.setStatus(status);
        d.setProvider("openai-compatible");
        d.setBaseUrl("https://api.openai.com/v1");
        d.setModelName("gpt-4o");
        d.setEncryptedSecret(secretCipher);
        d.setIsEnabled(1);
        return d;
    }

    @Test
    void createPrivateDraft_encryptsSecretAndRequiresOwner() {
        when(draftRepo.existsActiveDraft(1L, "gpt-4o", "PRIVATE", null)).thenReturn(false);
        doAnswer(inv -> {
                    inv.getArgument(0, AgentModelDraft.class).setId(10L);
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentModelDraft.class));
        when(draftRepo.findById(10L)).thenReturn(draft(10L, "DRAFT", "PRIVATE", null));

        CreateModelCommand cmd = new CreateModelCommand(
                "gpt-4o",
                "PRIVATE",
                "",
                "openai-compatible",
                "https://api.openai.com/v1",
                "gpt-4o",
                null,
                null,
                null,
                "sk-secret-123",
                "",
                1L);
        var view = service.createDraft(cmd);

        assertThat(view.id()).isEqualTo(10L);
        org.mockito.ArgumentCaptor<AgentModelDraft> captor = org.mockito.ArgumentCaptor.forClass(AgentModelDraft.class);
        verify(draftRepo).insert(captor.capture());
        String stored = captor.getValue().getEncryptedSecret();
        assertThat(stored).isNotBlank();
        assertThat(stored).isNotEqualTo("sk-secret-123");
        assertThat(cipher.decrypt(stored)).isEqualTo("sk-secret-123");
        assertThat(captor.getValue().getCapabilities()).contains("tool_use");
    }

    @Test
    void createDraft_rejectsMissingSecret() {
        CreateModelCommand cmd = new CreateModelCommand(
                "gpt-4o",
                "PRIVATE",
                "",
                "openai-compatible",
                "https://api.openai.com/v1",
                "gpt-4o",
                null,
                null,
                null,
                null,
                "",
                1L);
        assertThatThrownBy(() -> service.createDraft(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配置 API Key");
    }

    @Test
    void createPrivateDraft_rejectsMissingOwner() {
        CreateModelCommand cmd = new CreateModelCommand(
                "gpt-4o",
                "PRIVATE",
                "",
                "openai-compatible",
                "https://api.openai.com/v1",
                "gpt-4o",
                null,
                null,
                null,
                "sk",
                "",
                0L);
        assertThatThrownBy(() -> service.createDraft(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("当前登录用户");
    }

    @Test
    void createDraft_rejectsHttpUrl() {
        when(draftRepo.existsActiveDraft(1L, "gpt-4o", "PRIVATE", null)).thenReturn(false);
        CreateModelCommand cmd = new CreateModelCommand(
                "gpt-4o",
                "PRIVATE",
                "",
                "openai-compatible",
                "http://api.openai.com/v1",
                "gpt-4o",
                null,
                null,
                null,
                "sk",
                "",
                1L);
        assertThatThrownBy(() -> service.createDraft(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("https");
    }

    @Test
    void approve_officialRequiresPendingReview() {
        AgentModelDraft d = draft(1L, "DRAFT", "OFFICIAL", cipher.encrypt("sk"));
        when(draftRepo.findById(1L)).thenReturn(d);
        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许该操作");
        verify(releaseRepo, never()).insert(any());
    }

    @Test
    void approve_privateFromDraft_probesAndInsertsRelease() {
        AgentModelDraft d = draft(1L, "DRAFT", "PRIVATE", cipher.encrypt("sk"));
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class))).thenReturn(new ProbeResult(List.of("gpt-4o"), true, "ok"));
        when(releaseRepo.listByNameAllVersions(1L, "PRIVATE", "gpt-4o")).thenReturn(List.of());
        doAnswer(inv -> {
                    inv.getArgument(0, AgentModelRelease.class).setId(50L);
                    return 1;
                })
                .when(releaseRepo)
                .insert(any(AgentModelRelease.class));
        AgentModelRelease r = new AgentModelRelease();
        r.setId(50L);
        r.setName("gpt-4o");
        r.setScope("PRIVATE");
        r.setVersion(1);
        r.setStatus("PUBLISHED");
        r.setProvider("openai-compatible");
        r.setBaseUrl("https://api.openai.com/v1");
        r.setModelName("gpt-4o");
        when(releaseRepo.findById(50L)).thenReturn(r);

        var view = service.approve(1L);

        org.mockito.ArgumentCaptor<AgentModelRelease> captor =
                org.mockito.ArgumentCaptor.forClass(AgentModelRelease.class);
        verify(releaseRepo).insert(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getScope()).isEqualTo("PRIVATE");
        verify(draftRepo).updateStatus(1L, "CONSUMED", "", null, null);
        assertThat(view.id()).isEqualTo(50L);
    }

    @Test
    void approve_probeMismatch_doesNotInsertRelease() {
        AgentModelDraft d = draft(1L, "DRAFT", "PRIVATE", cipher.encrypt("sk"));
        when(draftRepo.findById(1L)).thenReturn(d);
        when(probePort.probe(any(ProbeCommand.class))).thenReturn(new ProbeResult(List.of("other"), false, "missing"));
        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未包含");
        verify(releaseRepo, never()).insert(any());
    }

    @Test
    void listAvailable_officialPlusOwnedPrivate() {
        AgentModelRelease official = new AgentModelRelease();
        official.setId(1L);
        official.setName("official");
        official.setScope("OFFICIAL");
        official.setStatus("PUBLISHED");
        official.setVersion(1);
        official.setProvider("openai-compatible");
        official.setBaseUrl("https://api.openai.com/v1");
        official.setModelName("gpt-4o");
        AgentModelRelease priv = new AgentModelRelease();
        priv.setId(2L);
        priv.setName("mine");
        priv.setScope("PRIVATE");
        priv.setOwnerUserId(7L);
        priv.setStatus("PUBLISHED");
        priv.setVersion(1);
        priv.setProvider("anthropic");
        priv.setBaseUrl("https://api.anthropic.com/v1");
        priv.setModelName("claude");
        when(releaseRepo.listOfficialPublished()).thenReturn(List.of(official));
        when(releaseRepo.listPrivatePublishedByOwner(7L)).thenReturn(List.of(priv));

        var views = service.listAvailable(7L);
        assertThat(views).hasSize(2);
        assertThat(views.get(0).name()).isEqualTo("official");
        assertThat(views.get(1).name()).isEqualTo("mine");
    }

    @Test
    void requireUsableRelease_rejectsOthersPrivate() {
        AgentModelRelease priv = new AgentModelRelease();
        priv.setId(2L);
        priv.setScope("PRIVATE");
        priv.setOwnerUserId(7L);
        priv.setStatus("PUBLISHED");
        when(releaseRepo.findById(2L)).thenReturn(priv);
        assertThatThrownBy(() -> service.requireUsableRelease(2L, 8L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void probeCatalog_returnsRemoteIdsWithoutRequiringMatch() {
        when(probePort.probe(any(ProbeCommand.class)))
                .thenReturn(new ProbeResult(List.of("gpt-4o", "gpt-4.1"), true, "ok"));
        var result = service.probeCatalog("openai-compatible", "https://api.openai.com/v1", "sk");
        assertThat(result.remoteModelIds()).containsExactly("gpt-4o", "gpt-4.1");
    }

    @Test
    void probeCatalog_rejectsEmptyCatalog() {
        when(probePort.probe(any(ProbeCommand.class))).thenReturn(new ProbeResult(List.of(), false, "empty"));
        assertThatThrownBy(() -> service.probeCatalog("openai-compatible", "https://api.openai.com/v1", "sk"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目录为空");
    }

    @Test
    void createDrafts_insertsEachSelectedModel() {
        when(draftRepo.existsActiveDraft(eq(1L), anyString(), eq("PRIVATE"), isNull()))
                .thenReturn(false);
        doAnswer(inv -> {
                    AgentModelDraft row = inv.getArgument(0, AgentModelDraft.class);
                    if (row.getId() == null) {
                        row.setId("a".equals(row.getName()) ? 1L : 2L);
                    }
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentModelDraft.class));
        when(draftRepo.findById(1L)).thenReturn(draft(1L, "DRAFT", "PRIVATE", null));
        when(draftRepo.findById(2L)).thenReturn(draft(2L, "DRAFT", "PRIVATE", null));

        var views = service.createDrafts(new ModelControlService.BatchCreateModelCommand(
                "PRIVATE",
                "openai-compatible",
                "https://api.openai.com/v1",
                "sk",
                null,
                null,
                "",
                1L,
                List.of(
                        new ModelControlService.ModelDraftItem("a", "gpt-4o", "", null, null, 500_000L),
                        new ModelControlService.ModelDraftItem("b", "gpt-4.1", "vision", null, null, 1_000_000L))));
        assertThat(views).hasSize(2);
        org.mockito.ArgumentCaptor<AgentModelDraft> captor = org.mockito.ArgumentCaptor.forClass(AgentModelDraft.class);
        verify(draftRepo, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AgentModelDraft::getModelName)
                .containsExactly("gpt-4o", "gpt-4.1");
        assertThat(captor.getAllValues())
                .extracting(AgentModelDraft::getContextLength)
                .containsExactly(500_000L, 1_000_000L);
    }
}
