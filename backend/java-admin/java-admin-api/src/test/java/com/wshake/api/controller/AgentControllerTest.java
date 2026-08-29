package com.wshake.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.api.dto.CreateAgentRevisionRequest;
import com.wshake.api.dto.UpdateAgentRevisionRequest;
import com.wshake.common.request.RequestContext;
import com.wshake.service.agent.AgentControlModels.AgentRevisionView;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.agent.AgentControlService;
import io.github.linpeilie.Converter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentControllerTest {

    private final AgentControlService agentControlService = mock(AgentControlService.class);
    private final AgentController controller = new AgentController(agentControlService, new Converter());

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

    private static AgentRevisionView revision(Long id, Long definitionId, String status) {
        return new AgentRevisionView(id, definitionId, status, null, "prompt", null, null, null, null, "", null, null);
    }
}
