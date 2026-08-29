package com.wshake.service.agent;

import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import com.wshake.service.agent.AgentControlModels.AgentRunPlan;
import reactor.core.publisher.Flux;

/** API 与 Agent 运行基础设施之间的领域端口。 */
public interface AgentRuntimeGateway {

    Flux<AgentRunEvent> run(AgentRunPlan plan, String requestId, String message);

    AgentRunEvent cancel(Long sessionId, String requestId);

    Flux<AgentRunEvent> resume(Long sessionId, String requestId);
}
