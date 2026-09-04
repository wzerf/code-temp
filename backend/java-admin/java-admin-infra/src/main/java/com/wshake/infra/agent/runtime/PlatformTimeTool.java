package com.wshake.infra.agent.runtime;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 平台受信工具：返回当前平台时间（Asia/Shanghai）。
 *
 * <p>docs/agent-module-architecture.md §5.6：运行面仅按 {@code permission_policy.allowedTools}
 * 放行受信 Java Tool，当前白名单工具为 {@code get_platform_time}。
 *
 * @author wshake
 */
public final class PlatformTimeTool implements AgentTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    @Override
    public String getName() {
        return "get_platform_time";
    }

    @Override
    public String getDescription() {
        return "获取平台当前时间(Asia/Shanghai)。无需参数,返回形如 2026-09-04 10:00:00 CST 的字符串。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(FORMATTER);
        return Mono.just(ToolResultBlock.text(now));
    }
}
