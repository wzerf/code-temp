package com.wshake.infra.agent.runtime;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 运行面配置（对应 {@code app.agent-runtime.*}）。
 *
 * @author wshake
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.agent-runtime")
public class AgentRuntimeProperties {

    /** SSE 单次运行最长执行时间；超过按运行失败处理（前端可续接）。 */
    private Duration runTimeout = Duration.ofMinutes(10);

    /** Agent 单次 ReAct 最大迭代数（防失控循环）。 */
    private int maxIters = 20;

    /** HarnessAgent 会话状态 Redis key 前缀（须带模块名，避免与其他 Redis 业务冲突）。 */
    private String stateKeyPrefix = "agent:runtime:agentscope:";

    /** 运行幂等/状态 Redis key 前缀。 */
    private String runKeyPrefix = "agent:runtime:run:";

    // ---------- AG-UI 事件开关（对齐 docs/agent-conversation-architecture.md §6.1） ----------

    /** 输出 REASONING_MESSAGE_*（思考链）事件。 */
    private boolean emitReasoning = true;

    /** 输出 TOOL_CALL_ARGS（工具参数增量）事件。 */
    private boolean emitToolCallArgs = true;

    /** 输出状态事件（StateSnapshot/StateDelta）。 */
    private boolean emitStateEvents = false;

    /** 输出 token 用量 CUSTOM 事件。 */
    private boolean emitTokenUsage = false;
}
