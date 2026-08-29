package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentSession;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentSessionRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentControlServiceTest {

    private final AgentDefinitionRepository definitionRepository = mock(AgentDefinitionRepository.class);
    private final AgentRevisionRepository revisionRepository = mock(AgentRevisionRepository.class);
    private final AgentSessionRepository sessionRepository = mock(AgentSessionRepository.class);
    private AgentControlService service;

    @BeforeEach
    void setUp() {
        service = new AgentControlService(definitionRepository, revisionRepository, sessionRepository);
    }

    @Test
    void publish_createsIndependentSnapshot_andUpdateRejectsIt() {
        AgentDefinition definition = definition(1L, 7L, 1, null);
        AgentRevision draft = draft(10L, 1L, "draft prompt");
        when(revisionRepository.findById(10L)).thenReturn(draft);
        when(definitionRepository.findById(1L)).thenReturn(definition);
        doAnswer(invocation -> {
                    AgentRevision row = invocation.getArgument(0);
                    row.setId(11L);
                    return null;
                })
                .when(revisionRepository)
                .insert(any(AgentRevision.class));

        var published = service.publish(10L, 7L);

        assertThat(published.id()).isEqualTo(11L);
        assertThat(published.status()).isEqualTo(AgentControlModels.REVISION_PUBLISHED);
        assertThat(published.sourceDraftRevisionId()).isEqualTo(10L);
        assertThat(definition.getCurrentPublishedRevisionId()).isEqualTo(11L);
        verify(definitionRepository).update(definition);

        when(revisionRepository.findById(11L)).thenReturn(published(11L, 1L, 10L, "draft prompt"));
        assertThatThrownBy(() -> service.updateDraft(
                        new UpdateRevisionCommand(
                                11L, "changed", true, null, false, null, false, null, false, null, false, null, false),
                        7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("only draft");
    }

    @Test
    void session_resolvesRevisionAtFirstRun_andKeepsItAfterRepublish() {
        AgentDefinition definition = definition(1L, 7L, 1, 11L);
        AgentSession[] persisted = new AgentSession[1];
        when(definitionRepository.findById(1L)).thenReturn(definition);
        when(revisionRepository.findById(11L)).thenReturn(published(11L, 1L, 10L, "first"));
        when(revisionRepository.findById(12L)).thenReturn(published(12L, 1L, 20L, "second"));
        doAnswer(invocation -> {
                    AgentSession row = invocation.getArgument(0);
                    row.setId(100L);
                    persisted[0] = row;
                    return null;
                })
                .when(sessionRepository)
                .insert(any(AgentSession.class));

        var session = service.createSession(1L, 7L);
        when(sessionRepository.findByIdAndOwnerUserId(100L, 7L)).thenReturn(persisted[0]);
        when(sessionRepository.bindRevisionIfUnbound(100L, 7L, 12L)).thenReturn(1L);
        definition.setCurrentPublishedRevisionId(12L);
        var resolved = service.resolveSessionRevision(session.id(), 7L);
        definition.setCurrentPublishedRevisionId(11L);
        var resolvedAgain = service.resolveSessionRevision(session.id(), 7L);

        assertThat(session.agentRevisionId()).isNull();
        assertThat(resolved.agentRevisionId()).isEqualTo(12L);
        assertThat(resolvedAgain.agentRevisionId()).isEqualTo(12L);
    }

    @Test
    void resolveSessionRevision_rejectsDefinitionWithoutPublishedRevision() {
        AgentSession session = new AgentSession();
        session.setId(100L);
        session.setAgentDefinitionId(1L);
        session.setOwnerUserId(7L);
        when(sessionRepository.findByIdAndOwnerUserId(100L, 7L)).thenReturn(session);
        when(definitionRepository.findById(1L)).thenReturn(definition(1L, 7L, 1, null));

        assertThatThrownBy(() -> service.resolveSessionRevision(100L, 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("revisionId is required");
    }

    @Test
    void prepareRun_bindsPublishedRevisionAndReturnsItsSnapshot() {
        AgentSession session = new AgentSession();
        session.setId(100L);
        session.setAgentDefinitionId(1L);
        session.setOwnerUserId(7L);
        AgentDefinition definition = definition(1L, 7L, 1, 11L);
        AgentRevision revision = published(11L, 1L, 10L, "prompt");
        revision.setModelConfig("{\"model\":\"test\"}");
        when(sessionRepository.findByIdAndOwnerUserId(100L, 7L)).thenReturn(session);
        when(definitionRepository.findById(1L)).thenReturn(definition);
        when(sessionRepository.bindRevisionIfUnbound(100L, 7L, 11L)).thenReturn(1L);
        when(revisionRepository.findById(11L)).thenReturn(revision);

        var plan = service.prepareRun(100L, 7L);

        assertThat(plan.agentRevisionId()).isEqualTo(11L);
        assertThat(plan.systemPrompt()).isEqualTo("prompt");
        assertThat(plan.modelConfig()).containsEntry("model", "test");
        verify(sessionRepository).bindRevisionIfUnbound(100L, 7L, 11L);
    }

    @Test
    void createDraft_rejectsDisabledDefinition() {
        when(definitionRepository.findById(1L)).thenReturn(definition(1L, 7L, 0, null));

        assertThatThrownBy(() -> service.createDraft(
                        new CreateRevisionCommand(1L, "prompt", Map.of(), null, null, null, ""), 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");
    }

    private static AgentDefinition definition(Long id, Long ownerUserId, int enabled, Long currentRevisionId) {
        AgentDefinition definition = new AgentDefinition();
        definition.setId(id);
        definition.setOwnerUserId(ownerUserId);
        definition.setIsEnabled(enabled);
        definition.setCurrentPublishedRevisionId(currentRevisionId);
        return definition;
    }

    private static AgentRevision draft(Long id, Long definitionId, String prompt) {
        AgentRevision revision = new AgentRevision();
        revision.setId(id);
        revision.setAgentDefinitionId(definitionId);
        revision.setStatus(AgentControlModels.REVISION_DRAFT);
        revision.setSystemPrompt(prompt);
        revision.setRemark("");
        revision.setIsEnabled(1);
        return revision;
    }

    private static AgentRevision published(Long id, Long definitionId, Long sourceDraftId, String prompt) {
        AgentRevision revision = draft(id, definitionId, prompt);
        revision.setStatus(AgentControlModels.REVISION_PUBLISHED);
        revision.setSourceDraftRevisionId(sourceDraftId);
        return revision;
    }
}
