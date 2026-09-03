package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentControlService.BindSkillCommand;
import com.wshake.service.agent.AgentControlService.CreateAgentCommand;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import com.wshake.service.repository.AgentRevisionMcpBindingRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentRevisionSkillBindingRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentControlService} 定义、发布/回滚、绑定语义。
 */
class AgentControlServiceTest {

    private final AgentDefinitionRepository defRepo = mock(AgentDefinitionRepository.class);
    private final AgentRevisionRepository revRepo = mock(AgentRevisionRepository.class);
    private final AgentRevisionSkillBindingRepository skillBindRepo = mock(AgentRevisionSkillBindingRepository.class);
    private final AgentRevisionMcpBindingRepository mcpBindRepo = mock(AgentRevisionMcpBindingRepository.class);
    private final AgentSkillReleaseRepository skillReleaseRepo = mock(AgentSkillReleaseRepository.class);
    private final AgentMcpReleaseRepository mcpReleaseRepo = mock(AgentMcpReleaseRepository.class);

    private AgentControlService service;

    @BeforeEach
    void init() {
        service = new AgentControlService(
                defRepo,
                revRepo,
                skillBindRepo,
                mcpBindRepo,
                skillReleaseRepo,
                mcpReleaseRepo,
                new io.github.linpeilie.Converter());
    }

    private AgentDefinition definition(Long id, Integer enabled) {
        AgentDefinition d = new AgentDefinition();
        d.setId(id);
        d.setName("support-agent");
        d.setOwnerUserId(1L);
        d.setIsEnabled(enabled);
        return d;
    }

    private AgentRevision revision(Long id, Long defId, String status) {
        AgentRevision r = new AgentRevision();
        r.setId(id);
        r.setAgentDefinitionId(defId);
        r.setStatus(status);
        r.setSystemPrompt("prompt");
        r.setIsEnabled(1);
        return r;
    }

    @Test
    void create_rejectsDuplicateName() {
        when(defRepo.findByName("support-agent")).thenReturn(definition(1L, 1));
        CreateAgentCommand cmd = new CreateAgentCommand("support-agent", "", "", 1L, 1);
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void publish_draftOnly_createsPublishedCopyWithNewId() {
        AgentDefinition def = definition(1L, 1);
        when(defRepo.findById(1L)).thenReturn(def);
        AgentRevision draft = revision(10L, 1L, "DRAFT");
        when(revRepo.findById(10L)).thenReturn(draft);
        when(revRepo.listByDefinitionId(1L)).thenReturn(List.of(draft));
        when(skillBindRepo.listByRevisionId(10L)).thenReturn(List.of());
        when(mcpBindRepo.listByRevisionId(10L)).thenReturn(List.of());

        doAnswer(inv -> {
                    AgentRevision row = inv.getArgument(0, AgentRevision.class);
                    if (row.getId() == null) {
                        row.setId(20L);
                    }
                    return 1;
                })
                .when(revRepo)
                .insert(any(AgentRevision.class));
        AgentRevision published = revision(20L, 1L, "PUBLISHED");
        when(revRepo.findById(20L)).thenReturn(published);

        var view = service.publish(10L);

        assertThat(view.status()).isEqualTo("PUBLISHED");
        verify(defRepo).updateCurrentPublishedRevisionId(1L, 20L);
    }

    @Test
    void publish_rejectsPublishedRevision() {
        AgentRevision published = revision(10L, 1L, "PUBLISHED");
        when(revRepo.findById(10L)).thenReturn(published);
        assertThatThrownBy(() -> service.publish(10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅 DRAFT");
    }

    @Test
    void publish_rejectsDisabledAgent() {
        AgentDefinition disabled = definition(1L, 0);
        when(defRepo.findById(1L)).thenReturn(disabled);
        AgentRevision draft = revision(10L, 1L, "DRAFT");
        when(revRepo.findById(10L)).thenReturn(draft);
        when(skillBindRepo.listByRevisionId(10L)).thenReturn(List.of());
        when(mcpBindRepo.listByRevisionId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publish(10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已禁用");
    }

    @Test
    void rollback_onlyToPublishedOfSameAgent() {
        AgentDefinition def = definition(1L, 1);
        def.setCurrentPublishedRevisionId(50L);
        when(defRepo.findById(1L)).thenReturn(def);
        AgentRevision published = revision(50L, 1L, "PUBLISHED");
        when(revRepo.findById(50L)).thenReturn(published);

        service.rollback(1L, 50L);
        verify(defRepo).updateCurrentPublishedRevisionId(1L, 50L);
    }

    @Test
    void rollback_rejectsDraftTarget() {
        when(defRepo.findById(1L)).thenReturn(definition(1L, 1));
        AgentRevision draft = revision(10L, 1L, "DRAFT");
        when(revRepo.findById(10L)).thenReturn(draft);
        assertThatThrownBy(() -> service.rollback(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已发布");
    }

    @Test
    void bindSkill_rejectsDuplicateNameInRevision() {
        AgentRevision draft = revision(10L, 1L, "DRAFT");
        when(revRepo.findById(10L)).thenReturn(draft);
        when(skillBindRepo.existsName(10L, "code-reviewer", null)).thenReturn(true);
        BindSkillCommand cmd = new BindSkillCommand(5L, "code-reviewer", "", 0);
        assertThatThrownBy(() -> service.bindSkillToRevision(10L, cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已绑定同名");
        verify(skillBindRepo, never()).insert(any());
    }
}
