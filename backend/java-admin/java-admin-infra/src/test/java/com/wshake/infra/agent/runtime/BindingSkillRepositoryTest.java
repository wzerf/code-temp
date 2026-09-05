package com.wshake.infra.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wshake.infra.agent.runtime.AgentBindingSnapshot.SkillEntry;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillReleaseResourceRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.harness.agent.skill.runtime.MarketplaceStager;
import io.agentscope.harness.agent.skill.runtime.MarketplaceStager.RepoBound;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 锁定 BindingSkillRepository.getSource() 可作为 Windows 路径段。
 *
 * <p>agentscope-harness 2.0.1 的 {@link MarketplaceStager} 会把仓库 source 拼进
 * {@code <workspace>/.skills-cache/<source-ns>/<skill>/}。{@code platform:session-N}
 * 在 Windows 上会抛 {@code InvalidPathException: Illegal char <:>}。
 */
class BindingSkillRepositoryTest {

    @TempDir
    Path workspace;

    @Test
    void getSource_isValidPathSegment_andMarketplaceStagerCanStage() throws Exception {
        AgentSkill skill = new AgentSkill("demo", "desc", "# demo skill", Map.of("notes.md", "hi"));
        BindingSkillRepository repo = new BindingSkillRepository(List.of(skill), "session-22");

        assertThat(repo.getSource()).doesNotContain(":");
        Path cacheDir =
                workspace.resolve(".skills-cache").resolve(repo.getSource()).resolve("demo");
        Files.createDirectories(cacheDir);
        assertThat(cacheDir).exists();

        MarketplaceStager stager = new MarketplaceStager(workspace);
        Map<AgentSkillRepository, String> ns = MarketplaceStager.resolveSourceNamespaces(List.of(repo));
        assertThatCode(() -> stager.stage(List.of(new RepoBound(skill, repo)), ns))
                .doesNotThrowAnyException();
        assertThat(workspace.resolve(".skills-cache").resolve(repo.getSource()).resolve("demo"))
                .exists();
    }

    @Test
    void assemble_usesPathSafeSourceOnSkills() {
        AgentSkillRelease release = new AgentSkillRelease();
        release.setId(7L);
        release.setName("demo");
        release.setDescription("desc");
        release.setSkillContent("# demo");
        release.setIsEnabled(1);

        AgentSkillReleaseRepository skillReleaseRepository = mock(AgentSkillReleaseRepository.class);
        AgentSkillReleaseResourceRepository resourceRepository = mock(AgentSkillReleaseResourceRepository.class);
        when(skillReleaseRepository.findById(7L)).thenReturn(release);
        when(resourceRepository.listByReleaseId(7L)).thenReturn(List.of());

        BindingSkillRepository repo = BindingSkillRepository.assemble(
                List.of(new SkillEntry("demo", 7L)), skillReleaseRepository, resourceRepository, "session-22");

        assertThat(repo.getSource()).isEqualTo("platform-session-22");
        assertThat(repo.getAllSkills())
                .singleElement()
                .extracting(AgentSkill::getSource)
                .isEqualTo("platform-session-22");
    }

    @Test
    void sourceNamespace_stripsWindowsIllegalChars() {
        BindingSkillRepository repo = new BindingSkillRepository(List.of(), "session:22");
        assertThat(repo.getSource()).doesNotContain(":", "<", ">", "\"", "|", "?", "*");
        assertThatCode(() -> Files.createDirectories(workspace.resolve(repo.getSource())))
                .doesNotThrowAnyException();
    }
}
