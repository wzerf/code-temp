package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.GitSkillSourceModels.CreateCommand;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitSkillSourceServiceTest {

    private final AgentSkillGitSourceRepository sourceRepository = mock(AgentSkillGitSourceRepository.class);
    private GitSkillSourceService service;

    @BeforeEach
    void setUp() {
        service = new GitSkillSourceService(
                sourceRepository,
                mock(AgentSkillGitSyncRepository.class),
                mock(AgentSkillDraftRepository.class),
                mock(SkillControlService.class),
                new GitSkillSourceProperties());
    }

    @Test
    void create_privateSource_persistsOnlyCredentialReference() {
        var source = service.create(new CreateCommand(
                7L, false, "PRIVATE", "https://github.com/agentscope-ai/agentscope-java.git", "main", "skills", "git-secret-ref"));

        assertThat(source.scope()).isEqualTo("PRIVATE");
        assertThat(source.hasSecretRef()).isTrue();
        verify(sourceRepository).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_marketSource_requiresAdministrator() {
        assertThatThrownBy(() -> service.create(new CreateCommand(
                        7L, false, "MARKET", "https://github.com/agentscope-ai/agentscope-java.git", "main", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("administrator");
    }

    @Test
    void create_rejectsNonHttpsUrl() {
        assertThatThrownBy(() -> service.create(new CreateCommand(
                        7L, false, "PRIVATE", "file:///tmp/skills", "HEAD", "", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTPS");
    }
}
