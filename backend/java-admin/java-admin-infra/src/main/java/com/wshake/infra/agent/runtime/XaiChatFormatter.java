package com.wshake.infra.agent.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;

final class XaiChatFormatter extends OpenAIChatFormatter {

    @Override
    protected OpenAIMessage convertMessage(Msg msg, boolean hasMedia) {
        OpenAIMessage message = super.convertMessage(msg, hasMedia);
        if (!"user".equals(message.getRole())) {
            message.setName(null);
        }
        return message;
    }
}
