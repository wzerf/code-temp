package com.wshake.infra.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class XaiChatFormatterTest {

    private final XaiChatFormatter formatter = new XaiChatFormatter();

    @Test
    void removesNamesFromNonUserMessages() {
        List<OpenAIMessage> messages = formatter.format(List.of(
                new SystemMessage("platform-agent", "system"),
                new UserMessage("operator", "hello"),
                new AssistantMessage("platform-agent", "answer")));

        assertThat(messages)
                .extracting(OpenAIMessage::getRole, OpenAIMessage::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("system", null),
                        org.assertj.core.groups.Tuple.tuple("user", "operator"),
                        org.assertj.core.groups.Tuple.tuple("assistant", null));
    }
}
