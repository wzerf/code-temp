package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 对外稳定的 Agent SSE 事件载荷；不暴露 AgentScope SDK 事件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentRunEvent.class)
public class AgentRunEventVO {

    private String type;
    private String requestId;
    private Long sessionId;
    private Long agentRevisionId;
    private String text;
    private String toolName;
    private String message;
}
