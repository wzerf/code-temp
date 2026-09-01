package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wshake.common.exception.BizException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSkillMarkdownTest {

    @Test
    void parse_readsNameAndDescription() {
        var frontmatter = AgentSkillMarkdown.parse("""
                ---
                name: code-reviewer
                description: Review pull requests
                ---
                Use the checklist.
                """);

        assertThat(frontmatter.name()).isEqualTo("code-reviewer");
        assertThat(frontmatter.description()).isEqualTo("Review pull requests");
    }

    @Test
    void parse_rejectsMissingFrontmatter() {
        assertThatThrownBy(() -> AgentSkillMarkdown.parse("no frontmatter"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("frontmatter");
    }

    @Test
    void hash_isStableForSortedResources() {
        String content = "---\nname: a\ndescription: b\n---\nbody";
        String first = AgentSkillContentHash.sha256(content, Map.of("b.md", "2", "a.md", "1"));
        String second = AgentSkillContentHash.sha256(content, Map.of("a.md", "1", "b.md", "2"));

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void validateResourcePath_rejectsTraversal() {
        assertThatThrownBy(() -> AgentSkillMarkdown.validateResourcePath("../secret"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("relative");
    }
}
