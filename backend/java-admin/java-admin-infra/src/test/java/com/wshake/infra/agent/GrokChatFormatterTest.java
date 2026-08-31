package com.wshake.infra.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrokChatFormatterTest {

    @Test
    void needsGrokFormatter_matchesModelOrXaiBaseUrl() {
        assertThat(GrokChatFormatter.needsGrokFormatter("grok-4.5", "https://example.test/v1"))
                .isTrue();
        assertThat(GrokChatFormatter.needsGrokFormatter("gpt-5.6", "https://api.x.ai/v1"))
                .isTrue();
        assertThat(GrokChatFormatter.needsGrokFormatter("gpt-5.6", "https://example.test/v1"))
                .isFalse();
    }

    @Test
    void stripNonUserName_keepsUserNameAndClearsOthers() {
        OpenAIMessage user =
                OpenAIMessage.builder().role("user").name("alice").content("hi").build();
        OpenAIMessage assistant = OpenAIMessage.builder()
                .role("assistant")
                .name("agent-1")
                .content("ok")
                .build();
        OpenAIMessage system = OpenAIMessage.builder()
                .role("system")
                .name("system")
                .content("prompt")
                .build();
        OpenAIMessage tool = OpenAIMessage.builder()
                .role("tool")
                .name("clock")
                .content("{}")
                .toolCallId("c1")
                .build();

        assertThat(GrokChatFormatter.stripNonUserName(user).getName()).isEqualTo("alice");
        assertThat(GrokChatFormatter.stripNonUserName(assistant).getName()).isNull();
        assertThat(GrokChatFormatter.stripNonUserName(system).getName()).isNull();
        assertThat(GrokChatFormatter.stripNonUserName(tool).getName()).isNull();
    }

    @Test
    void format_stripsAssistantAndSystemNames_toAvoidXai400() {
        // 复现 xAI 400：Only message of role `user` can have a name.
        List<OpenAIMessage> formatted = new GrokChatFormatter()
                .format(List.of(
                        new SystemMessage("system", "you are helpful"),
                        new UserMessage("alice", "hi"),
                        new AssistantMessage("agent-revision-1", "hello")));

        assertThat(formatted).hasSize(3);
        assertThat(formatted.get(0).getRole()).isEqualTo("system");
        assertThat(formatted.get(0).getName()).isNull();
        assertThat(formatted.get(1).getRole()).isEqualTo("user");
        assertThat(formatted.get(1).getName()).isEqualTo("alice");
        assertThat(formatted.get(2).getRole()).isEqualTo("assistant");
        assertThat(formatted.get(2).getName()).isNull();
    }
}
