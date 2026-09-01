package com.wshake.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.api.dto.CancelAgentRunRequest;
import com.wshake.api.dto.CreateAgentRevisionRequest;
import com.wshake.api.dto.UpdateAgentRevisionRequest;
import com.wshake.common.request.RequestContext;
import com.wshake.service.agent.AgentControlModels.AgentRevisionView;
import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import com.wshake.service.agent.AgentControlModels.AgentSessionView;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.agent.AgentControlService;
import com.wshake.service.agent.AgentRuntimeGateway;
import io.github.linpeilie.Converter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentControllerTest {

    private final AgentControlService agentControlService = mock(AgentControlService.class);
    private final AgentRuntimeGateway agentRuntimeGateway = mock(AgentRuntimeGateway.class);
    private final AgentController controller =
            new AgentController(agentControlService, new Converter(), agentRuntimeGateway);

    @Test
    void createDraft_usesPathDefinitionId() {
        when(agentControlService.createDraft(any(), any())).thenReturn(revision(10L, 1L, "DRAFT"));
        CreateAgentRevisionRequest request = new CreateAgentRevisionRequest();
        request.setSystemPrompt("prompt");
        request.setModelConfig(Map.of("model", "test"));

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            var result = controller.createDraft(1L, request);
            assertThat(result.getCode()).isZero();
        } finally {
            RequestContext.close();
        }

        ArgumentCaptor<CreateRevisionCommand> captor = ArgumentCaptor.forClass(CreateRevisionCommand.class);
        verify(agentControlService).createDraft(captor.capture(), any());
        assertThat(captor.getValue().agentDefinitionId()).isEqualTo(1L);
        assertThat(captor.getValue().systemPrompt()).isEqualTo("prompt");
    }

    @Test
    void updateDraft_preservesFieldPresence() {
        when(agentControlService.updateDraft(any(), any())).thenReturn(revision(10L, 1L, "DRAFT"));
        UpdateAgentRevisionRequest request = new UpdateAgentRevisionRequest();
        request.setModelConfig(null);

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            var result = controller.updateDraft(10L, request);
            assertThat(result.getCode()).isZero();
        } finally {
            RequestContext.close();
        }

        ArgumentCaptor<UpdateRevisionCommand> captor = ArgumentCaptor.forClass(UpdateRevisionCommand.class);
        verify(agentControlService).updateDraft(captor.capture(), any());
        assertThat(captor.getValue().modelConfigPresent()).isTrue();
        assertThat(captor.getValue().modelConfig()).isNull();
        assertThat(captor.getValue().systemPromptPresent()).isFalse();
    }

    @Test
    void cancel_returnsRuntimeStateWithoutResolvingRevision() {
        when(agentControlService.getSession(20L, 7L))
                .thenReturn(new AgentSessionView(20L, 1L, null, 7L, "ACTIVE", null, null));
        when(agentRuntimeGateway.cancel(20L, "request-1"))
                .thenReturn(new AgentRunEvent("CANCELLED", "request-1", 20L, 10L, null, null, null));
        CancelAgentRunRequest request = new CancelAgentRunRequest();
        request.setRequestId("request-1");

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            var result = controller.cancel(20L, request);
            assertThat(result.getData().getType()).isEqualTo("CANCELLED");
            assertThat(result.getData().getAgentRevisionId()).isEqualTo(10L);
        } finally {
            RequestContext.close();
        }

        verify(agentControlService).getSession(20L, 7L);
        verify(agentRuntimeGateway).cancel(20L, "request-1");
    }

    @Test
    void resume_checksSessionOwnershipBeforeSubscribing() {
        when(agentControlService.getSession(20L, 7L))
                .thenReturn(new AgentSessionView(20L, 1L, 10L, 7L, "ACTIVE", null, null));
        when(agentRuntimeGateway.resume(20L, "request-1")).thenReturn(reactor.core.publisher.Flux.empty());

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            controller.resume(20L, "request-1");
        } finally {
            RequestContext.close();
        }

        verify(agentControlService).getSession(20L, 7L);
        verify(agentRuntimeGateway).resume(20L, "request-1");
    }

    @Test
    void listSessions_usesCurrentUser() {
        when(agentControlService.listSessions(1L, 7L))
                .thenReturn(java.util.List.of(new AgentSessionView(20L, 1L, 10L, 7L, "ACTIVE", null, null)));

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            var result = controller.listSessions(1L);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().getFirst().getId()).isEqualTo(20L);
        } finally {
            RequestContext.close();
        }

        verify(agentControlService).listSessions(1L, 7L);
    }

    private static AgentRevisionView revision(Long id, Long definitionId, String status) {
        return new AgentRevisionView(
                id, definitionId, status, null, "prompt", null, null, null, null, "", null, null, java.util.List.of());
    }
}
