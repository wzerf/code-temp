package com.wshake.infra.agent.formatter;

import io.agentscope.core.message.Msg;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;

public class XaiChatFormatter extends OpenAIChatFormatter {

    @Override
    protected OpenAIMessage convertMessage(Msg msg, boolean hasMedia) {
        OpenAIMessage message = super.convertMessage(msg, hasMedia);
        if (!"user".equals(message.getRole())) {
            message.setName(null);
        }
        return message;
    }
}
