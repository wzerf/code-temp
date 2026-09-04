package com.wshake.infra.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSessionService;
import com.wshake.service.agent.AgentSessionService.AgentSessionView;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentSession;
import com.wshake.service.entity.AgentSessionModelBinding;
import com.wshake.service.model.ModelControlService;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentSessionModelBindingRepository;
import com.wshake.service.repository.AgentSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link AgentRunPlanner} 运行计划装配（会话→Revision→模型选择）。 */
class AgentRunPlannerTest {

    private AgentSessionRepository sessionRepository;
    private AgentSessionModelBindingRepository modelBindingRepository;
    private AgentRevisionRepository revisionRepository;
    private ModelControlService modelControlService;
    private AgentSecretCipher secretCipher;
    private AgentSessionService sessionService;
    private AgentBindingLoader bindingLoader;
    private AgentRunPlanner planner;

    @BeforeEach
    void init() {
        sessionRepository = mock(AgentSessionRepository.class);
        modelBindingRepository = mock(AgentSessionModelBindingRepository.class);
        revisionRepository = mock(AgentRevisionRepository.class);
        modelControlService = mock(ModelControlService.class);
        secretCipher = mock(AgentSecretCipher.class);
        sessionService = mock(AgentSessionService.class);
        bindingLoader = mock(AgentBindingLoader.class);
        when(bindingLoader.load(anyLong(), anyLong())).thenReturn(AgentBindingSnapshot.merge(null, null, null, null));
        planner = new AgentRunPlanner(
                sessionService,
                sessionRepository,
                modelBindingRepository,
                revisionRepository,
                modelControlService,
                secretCipher,
                bindingLoader,
                new ObjectMapper());
    }

    private AgentSession session(Long id, Long definitionId, Long revisionId, Long owner) {
        AgentSession s = new AgentSession();
        s.setId(id);
        s.setAgentDefinitionId(definitionId);
        s.setAgentRevisionId(revisionId);
        s.setOwnerUserId(owner);
        return s;
    }

    private AgentRevision revision(Long id, String systemPrompt, String modelConfig, String permissionPolicy) {
        AgentRevision r = new AgentRevision();
        r.setId(id);
        r.setSystemPrompt(systemPrompt);
        r.setModelConfig(modelConfig);
        r.setPermissionPolicy(permissionPolicy);
        r.setIsEnabled(1);
        return r;
    }

    private AgentModelRelease release(Long id, Long owner) {
        AgentModelRelease m = new AgentModelRelease();
        m.setId(id);
        m.setOwnerUserId(owner);
        m.setProvider("openai-compatible");
        m.setBaseUrl("https://example.com/v1");
        m.setModelName("test-model");
        m.setEncryptedSecret("enc");
        m.setStatus("PUBLISHED");
        return m;
    }

    /** mock 一次成功装配链：会话未绑定 → bindSessionRevision 固定 → revision/release/密钥就绪。 */
    private void mockAssembleChain(
            AgentSession session, AgentRevision revision, AgentModelRelease release, Long boundRevisionId) {
        when(sessionRepository.findById(session.getId())).thenReturn(session);
        AgentSessionView view = new AgentSessionView(
                session.getId(),
                session.getAgentDefinitionId(),
                boundRevisionId,
                session.getOwnerUserId(),
                "ACTIVE",
                null,
                "",
                1,
                0L,
                null,
                null,
                0L,
                0L);
        when(sessionService.bindSessionRevision(session.getId())).thenReturn(view);
        when(revisionRepository.findById(boundRevisionId)).thenReturn(revision);
        when(modelControlService.requireUsableRelease(release.getId(), session.getOwnerUserId()))
                .thenReturn(release);
        when(secretCipher.decrypt(release.getEncryptedSecret())).thenReturn("plain-key");
    }

    @Test
    void fallsBackToRevisionDefaultModelWhenNoSessionBinding() {
        AgentSession session = session(1L, 10L, null, 100L);
        AgentRevision revision =
                revision(20L, "prompt", "{\"default_model_release_id\": 7001}", "{\"allowedTools\":[]}");
        AgentModelRelease release = release(7001L, 0L);
        mockAssembleChain(session, revision, release, 20L);

        AgentRunPlan plan = planner.plan(session.getId(), 100L);

        assertThat(plan.sessionId()).isEqualTo(1L);
        assertThat(plan.revisionId()).isEqualTo(20L);
        assertThat(plan.modelReleaseId()).isEqualTo(7001L);
        assertThat(plan.modelName()).isEqualTo("test-model");
        assertThat(plan.plainSecret()).isEqualTo("plain-key");
        assertThat(plan.allowedTools()).isEmpty();
        assertThat(plan.systemPrompt()).isEqualTo("prompt");
    }

    @Test
    void sessionModelBindingTakesPriorityOverRevisionDefault() {
        AgentSession session = session(1L, 10L, 20L, 100L);
        AgentRevision revision = revision(20L, "prompt", "{\"default_model_release_id\": 7001}", null);
        AgentModelRelease release = release(7002L, 100L);
        mockAssembleChain(session, revision, release, 20L);
        AgentSessionModelBinding binding = new AgentSessionModelBinding();
        binding.setModelReleaseId(7002L);
        when(modelBindingRepository.findBySessionId(1L)).thenReturn(binding);

        AgentRunPlan plan = planner.plan(1L, 100L);

        assertThat(plan.modelReleaseId()).isEqualTo(7002L);
    }

    @Test
    void rejectsWhenNoModelConfigured() {
        AgentSession session = session(1L, 10L, null, 100L);
        AgentRevision revision = revision(20L, "prompt", null, null);
        AgentModelRelease release = release(7001L, 0L);
        mockAssembleChain(session, revision, release, 20L);
        when(modelBindingRepository.findBySessionId(1L)).thenReturn(null);

        assertThatThrownBy(() -> planner.plan(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未选模型");
    }

    @Test
    void rejectsUnsupportedProvider() {
        AgentSession session = session(1L, 10L, null, 100L);
        AgentRevision revision = revision(20L, "prompt", "{\"default_model_release_id\": 7001}", null);
        AgentModelRelease release = release(7001L, 0L);
        release.setProvider("anthropic");
        mockAssembleChain(session, revision, release, 20L);

        assertThatThrownBy(() -> planner.plan(1L, 100L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("openai-compatible");
    }

    @Test
    void rejectsNonOwnerAccess() {
        AgentSession session = session(1L, 10L, 20L, 100L);
        when(sessionRepository.findById(1L)).thenReturn(session);

        assertThatThrownBy(() -> planner.plan(1L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }
}
