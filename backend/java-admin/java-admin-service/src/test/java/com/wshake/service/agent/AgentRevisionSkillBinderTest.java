package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.AgentControlModels.SkillBindingCommand;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.repository.AgentRevisionSkillBindingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRevisionSkillBinderTest {

    private final AgentRevisionSkillBindingRepository bindingRepository =
            mock(AgentRevisionSkillBindingRepository.class);
    private final SkillControlService skillControlService = mock(SkillControlService.class);
    private AgentRevisionSkillBinder binder;

    @BeforeEach
    void setUp() {
        binder = new AgentRevisionSkillBinder(bindingRepository, skillControlService);
    }

    @Test
    void replaceDraftBindings_rejectsSameNameWithoutWinner() {
        when(skillControlService.requirePublishedRelease(1L)).thenReturn(release(1L, "code-reviewer", "MARKET", 0L));
        when(skillControlService.requirePublishedRelease(2L)).thenReturn(release(2L, "code-reviewer", "PRIVATE", 7L));
        when(skillControlService.canBind(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertThatThrownBy(() -> binder.replaceDraftBindings(
                        10L, List.of(new SkillBindingCommand(1L, false), new SkillBindingCommand(2L, false)), 7L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("overrideWinner");
    }

    @Test
    void replaceDraftBindings_keepsDeclaredWinner() {
        when(skillControlService.requirePublishedRelease(1L)).thenReturn(release(1L, "code-reviewer", "MARKET", 0L));
        when(skillControlService.requirePublishedRelease(2L)).thenReturn(release(2L, "code-reviewer", "PRIVATE", 7L));
        when(skillControlService.canBind(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        binder.replaceDraftBindings(
                10L, List.of(new SkillBindingCommand(1L, false), new SkillBindingCommand(2L, true)), 7L);

        ArgumentCaptor<List<AgentRevisionSkillBinding>> captor = ArgumentCaptor.captor();
        verify(bindingRepository).replace(eq(10L), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getSkillReleaseId()).isEqualTo(2L);
        assertThat(captor.getValue().get(0).getOverrideWinner()).isEqualTo(1);
    }

    @Test
    void snapshotsForRun_usesBoundReleaseNotLaterMarket() {
        AgentRevisionSkillBinding binding = new AgentRevisionSkillBinding();
        binding.setSkillReleaseId(20L);
        binding.setSkillName("code-reviewer");
        binding.setContentHash("hash-v1");
        when(bindingRepository.listByRevisionId(11L)).thenReturn(List.of(binding));
        AgentSkillRelease frozen = release(20L, "code-reviewer", "MARKET", 0L);
        frozen.setSkillContent("old instructions");
        frozen.setContentHash("hash-v1");
        frozen.setSource("mysql");
        when(skillControlService.requireReleaseForRun(20L)).thenReturn(frozen);
        when(skillControlService.resourcesOf(20L)).thenReturn(java.util.Map.of());

        var snapshots = binder.snapshotsForRun(11L);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).skillContent()).isEqualTo("old instructions");
        assertThat(snapshots.get(0).contentHash()).isEqualTo("hash-v1");
    }

    private static AgentSkillRelease release(Long id, String name, String visibility, Long ownerUserId) {
        AgentSkillRelease release = new AgentSkillRelease();
        release.setId(id);
        release.setName(name);
        release.setVisibility(visibility);
        release.setOwnerUserId(ownerUserId);
        release.setStatus(SkillControlModels.PUBLISHED);
        release.setIsEnabled(1);
        release.setContentHash("hash-" + id);
        release.setSkillContent("content-" + id);
        release.setSource("mysql");
        return release;
    }
}
