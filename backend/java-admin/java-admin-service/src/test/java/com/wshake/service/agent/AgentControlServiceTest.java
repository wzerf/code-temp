package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentControlModels.RevisionView;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentRevisionBindingRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import io.github.linpeilie.Converter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentControlService} publish / rollback 行为。
 */
class AgentControlServiceTest {

    private final AgentDefinitionRepository definitionRepository = mock(AgentDefinitionRepository.class);
    private final AgentRevisionRepository revisionRepository = mock(AgentRevisionRepository.class);
    private final AgentRevisionBindingRepository bindingRepository = mock(AgentRevisionBindingRepository.class);
    private AgentControlService service;

    private final AtomicLong revisionId = new AtomicLong(10);

    @BeforeEach
    void init() {
        service = new AgentControlService(definitionRepository, revisionRepository, bindingRepository, new Converter());
    }

    private AgentRevision draft(Long id, Long definitionId) {
        AgentRevision r = new AgentRevision();
        r.setId(id);
        r.setAgentDefinitionId(definitionId);
        r.setStatus("DRAFT");
        r.setSystemPrompt("sp");
        r.setModelConfig("{\"model\":\"gpt\"}");
        r.setPermissionPolicy("{\"allowedTools\":[]}");
        r.setIsEnabled(1);
        return r;
    }

    @Test
    void publish_copiesDraftAsPublishedAndUpdatesPointer() {
        AgentRevision draft = draft(10L, 1L);
        when(revisionRepository.findById(10L)).thenReturn(draft);
        when(bindingRepository.listSkillBindings(10L)).thenReturn(List.of());
        when(bindingRepository.listMcpBindings(10L)).thenReturn(List.of());

        java.util.concurrent.atomic.AtomicReference<AgentRevision> inserted =
                new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
                    AgentRevision r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(revisionId.incrementAndGet());
                    }
                    inserted.set(r);
                    when(revisionRepository.findById(r.getId())).thenReturn(r);
                    return null;
                })
                .when(revisionRepository)
                .insert(org.mockito.ArgumentMatchers.any());

        RevisionView result = service.publish(10L);

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.sourceDraftRevisionId()).isEqualTo(10L);
        verify(definitionRepository).updateCurrentPublishedRevision(1L, result.id());
    }

    @Test
    void publish_rejectsPublishedRevision() {
        AgentRevision published = draft(10L, 1L);
        published.setStatus("PUBLISHED");
        when(revisionRepository.findById(10L)).thenReturn(published);

        assertThatThrownBy(() -> service.publish(10L)).isInstanceOf(BizException.class);
        verify(revisionRepository, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rollback_rejectsDraftTarget() {
        AgentDefinition def = new AgentDefinition();
        def.setId(1L);
        when(definitionRepository.findById(1L)).thenReturn(def);

        AgentRevision draft = draft(20L, 1L);
        when(revisionRepository.findById(20L)).thenReturn(draft);

        assertThatThrownBy(() -> service.rollback(1L, 20L)).isInstanceOf(BizException.class);
        verify(definitionRepository, never())
                .updateCurrentPublishedRevision(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
