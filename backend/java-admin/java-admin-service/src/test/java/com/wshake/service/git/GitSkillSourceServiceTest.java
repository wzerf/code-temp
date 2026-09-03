package com.wshake.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSecretProperties;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.git.GitSkillSourceService.CreateGitSourceCommand;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillDraftResourceRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link GitSkillSourceService} 来源 CRUD 与安全校验。
 */
class GitSkillSourceServiceTest {

    private final AgentSkillGitSourceRepository sourceRepo = mock(AgentSkillGitSourceRepository.class);
    private final AgentSkillGitSyncRepository syncRepo = mock(AgentSkillGitSyncRepository.class);
    private final AgentSkillDraftRepository draftRepo = mock(AgentSkillDraftRepository.class);
    private final AgentSkillDraftResourceRepository draftResourceRepo = mock(AgentSkillDraftResourceRepository.class);

    private GitSkillSourceService service;

    @BeforeEach
    void init() {
        AgentSecretProperties props = new AgentSecretProperties();
        AgentSecretCipher cipher = new AgentSecretCipher(props);
        cipher.init();
        service = new GitSkillSourceService(sourceRepo, syncRepo, draftRepo, draftResourceRepo, cipher);
    }

    @Test
    void createSource_rejectsNonHttps() {
        CreateGitSourceCommand cmd =
                new CreateGitSourceCommand("PRIVATE", 1L, "git@github.com:org/skills.git", "main", "", null, "");
        assertThatThrownBy(() -> service.createSource(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void createSource_rejectsUserInfo() {
        CreateGitSourceCommand cmd = new CreateGitSourceCommand(
                "PRIVATE", 1L, "https://token@github.com/org/skills.git", "main", "", null, "");
        assertThatThrownBy(() -> service.createSource(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("user-info");
    }

    @Test
    void createSource_rejectsPrivateHost() {
        CreateGitSourceCommand cmd =
                new CreateGitSourceCommand("PRIVATE", 1L, "https://192.168.1.10/repo.git", "main", "", null, "");
        assertThatThrownBy(() -> service.createSource(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
    }

    @Test
    void createSource_rejectsLocalhost() {
        CreateGitSourceCommand cmd =
                new CreateGitSourceCommand("PRIVATE", 1L, "https://localhost/repo.git", "main", "", null, "");
        assertThatThrownBy(() -> service.createSource(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
    }

    @Test
    void createSource_rejectsBadScope() {
        CreateGitSourceCommand cmd =
                new CreateGitSourceCommand("PUBLIC", 1L, "https://github.com/org/skills.git", "main", "", null, "");
        assertThatThrownBy(() -> service.createSource(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void createSource_encryptsSecretAndInserts() {
        when(sourceRepo.listByScope("PRIVATE", 1L)).thenReturn(java.util.List.of());
        doAnswer(inv -> {
                    AgentSkillGitSource row = inv.getArgument(0, AgentSkillGitSource.class);
                    row.setId(9L);
                    return 1;
                })
                .when(sourceRepo)
                .insert(any(AgentSkillGitSource.class));

        CreateGitSourceCommand cmd = new CreateGitSourceCommand(
                "PRIVATE", 1L, "https://github.com/org/skills.git", "main", "skills", "ghp-secret", "note");
        AgentSkillGitSource saved = new AgentSkillGitSource();
        saved.setId(9L);
        saved.setUrl("https://github.com/org/skills.git");
        when(sourceRepo.findById(9L)).thenReturn(saved);

        var view = service.createSource(cmd);

        org.mockito.ArgumentCaptor<AgentSkillGitSource> captor =
                org.mockito.ArgumentCaptor.forClass(AgentSkillGitSource.class);
        org.mockito.Mockito.verify(sourceRepo).insert(captor.capture());
        AgentSkillGitSource row = captor.getValue();
        assertThat(row.getUrl()).isEqualTo("https://github.com/org/skills.git");
        assertThat(row.getRef()).isEqualTo("main");
        assertThat(row.getSubdirectory()).isEqualTo("skills");
        assertThat(row.getStatus()).isEqualTo("READY");
        // 密钥加密落库
        assertThat(row.getEncryptedSecret()).isNotBlank();
        assertThat(row.getEncryptedSecret()).isNotEqualTo("ghp-secret");
    }
}
