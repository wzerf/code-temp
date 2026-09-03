package com.wshake.service.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.wshake.service.agent.skill.SkillManageModels.SkillResourceCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SkillManageModels} 内容 hash 与资源路径校验。
 */
class SkillManageModelsTest {

    @Test
    void contentHash_isStableAndOrderIndependent() {
        String skill = "---\nname: a\ndescription: d\n---\nbody";
        String h1 = SkillManageModels.contentHash(
                skill, List.of(new SkillResourceCommand("b.txt", "b"), new SkillResourceCommand("a.txt", "a")));
        String h2 = SkillManageModels.contentHash(
                skill, List.of(new SkillResourceCommand("a.txt", "a"), new SkillResourceCommand("b.txt", "b")));
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
    }

    @Test
    void contentHash_differsWhenContentDiffers() {
        String skill = "---\nname: a\ndescription: d\n---\nbody";
        String h1 = SkillManageModels.contentHash(skill, List.of());
        String h2 = SkillManageModels.contentHash(skill + "x", List.of());
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void requireResourcePath_rejectsAbsoluteAndDotDot() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SkillManageModels.requireResourcePath("/etc/passwd"))
                .isInstanceOf(com.wshake.common.exception.BizException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SkillManageModels.requireResourcePath("../x"))
                .isInstanceOf(com.wshake.common.exception.BizException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> SkillManageModels.requireResourcePath("a\\b"))
                .isInstanceOf(com.wshake.common.exception.BizException.class);
    }
}
