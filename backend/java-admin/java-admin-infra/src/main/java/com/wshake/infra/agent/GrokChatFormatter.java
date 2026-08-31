package com.wshake.infra.agent;

import io.agentscope.core.message.Msg;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import java.util.Locale;

/**
 * Grok / xAI 兼容的 Chat Formatter。
 *
 * <p>xAI Chat Completions 约束：只有 {@code role=user} 的消息允许携带 {@code name}；
 * AgentScope 默认会给 assistant/system 也写入 name，导致 HTTP 400：
 * {@code Only message of role `user` can have a name.}
 */
public class GrokChatFormatter extends OpenAIChatFormatter {

    /** 根据模型名或 baseUrl 判断是否需要启用本 formatter。 */
    public static boolean needsGrokFormatter(String modelName, String baseUrl) {
        String model = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        String url = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        return model.contains("grok") || url.contains("api.x.ai") || url.contains("//x.ai/");
    }

    @Override
    protected OpenAIMessage convertMessage(Msg msg, boolean hasMedia) {
        return stripNonUserName(super.convertMessage(msg, hasMedia));
    }

    /** 清除非 user 消息上的 name；user 消息保留。 */
    static OpenAIMessage stripNonUserName(OpenAIMessage message) {
        if (message == null) {
            return null;
        }
        if (!"user".equals(message.getRole()) && message.getName() != null) {
            message.setName(null);
        }
        return message;
    }
}
