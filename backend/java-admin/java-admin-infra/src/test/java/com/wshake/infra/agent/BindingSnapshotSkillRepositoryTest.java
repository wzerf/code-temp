package com.wshake.infra.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.wshake.service.agent.AgentControlModels.SkillSnapshot;
import io.agentscope.core.skill.AgentSkill;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BindingSnapshotSkillRepositoryTest {

    @Test
    void listsOnlyBoundSnapshots_andRejectsWrites() {
        try (BindingSnapshotSkillRepository repository = new BindingSnapshotSkillRepository(List.of(new SkillSnapshot(
                "code-reviewer",
                "Review pull requests",
                "old instructions",
                "mysql",
                "hash-v1",
                Map.of("references/a.md", "a"))))) {
            assertThat(repository.getAllSkillNames()).containsExactly("code-reviewer");
            assertThat(repository.getSkill("code-reviewer").getSkillContent()).isEqualTo("old instructions");
            assertThat(repository.isWriteable()).isFalse();
            assertThat(repository.save(
                            List.of(new AgentSkill("code-reviewer", "new", "leaked market content", Map.of())), true))
                    .isFalse();
            assertThat(repository.getSkill("code-reviewer").getSkillContent()).isEqualTo("old instructions");
            assertThat(repository.delete("code-reviewer")).isFalse();
            assertThat(repository.skillExists("other")).isFalse();
        }
    }
}
