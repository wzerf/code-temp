package com.wshake.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.Result;
import com.wshake.common.result.ResultCode;
import com.wshake.infra.agent.runtime.AgentAguiService;
import io.agentscope.core.agui.model.RunAgentInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 对话运行面（AG-UI 标准 SSE 端点）。
 *
 * <p>对齐 docs/agent-conversation-architecture.md §4.3：前端 POST 标准 AG-UI
 * {@code RunAgentInput}（threadId=平台 sessionId），后端按会话装配 agent，
 * 经官方 AG-UI 适配器流式输出标准事件。
 *
 * <p>请求体反序列化：官方 agui 模型类用 Jackson 2 注解，而平台 HTTP 层是
 * Spring Boot 4 默认 Jackson 3（不识别 Jackson 2 注解），故此处手动用平台
 * Jackson 2 ObjectMapper bean 解析 {@code RunAgentInput}。
 *
 * <p>路径受 Sa-Token + Casbin 保护；Encrypt/Sign 中间件已对 {@code /events} 旁路
 * （见 EncryptFilter/SignFilter.shouldBypass），SSE 不被响应体缓冲。
 *
 * @author wshake
 */
@Tag(name = "Agent 运行面", description = "AG-UI SSE 事件流(运行 Agent 会话;首启固定 Revision)")
@RestController
@RequestMapping("/api/system/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentAguiController {

    private final AgentAguiService aguiService;
    private final ObjectMapper jackson2Mapper;

    @PostMapping(
            value = "/sessions/{sessionId}/events",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "运行 Agent 会话(AG-UI 标准 SSE);threadId 强制为路径 sessionId")
    public SseEmitter run(@PathVariable Long sessionId, HttpServletRequest request) {
        RunAgentInput input = parseBody(request);
        // threadId 以路径为准：会话隔离/状态存储/幂等都以平台 sessionId 为键,不信任 body
        RunAgentInput effective =
                input.getThreadId() != null && input.getThreadId().equals(String.valueOf(sessionId))
                        ? input
                        : RunAgentInput.builder()
                                .threadId(String.valueOf(sessionId))
                                .runId(input.getRunId())
                                .messages(input.getMessages())
                                .tools(input.getTools())
                                .context(input.getContext())
                                .state(input.getState())
                                .forwardedProps(input.getForwardedProps())
                                .resume(input.getResume())
                                .build();
        return aguiService.run(sessionId, RequestContext.userIdOrNull(), input.getRunId(), effective);
    }

    @GetMapping("/sessions/{sessionId}/events/history")
    @Operation(summary = "会话 AG-UI 事件历史回放(按序返回持久化事件 JSON 列表)")
    public Result<List<String>> history(@PathVariable Long sessionId) {
        return Result.ok(aguiService.history(sessionId, RequestContext.userIdOrNull()));
    }

    private RunAgentInput parseBody(HttpServletRequest request) {
        try {
            return jackson2Mapper.readValue(request.getInputStream().readAllBytes(), RunAgentInput.class);
        } catch (IOException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "AG-UI 请求体解析失败: " + e.getMessage());
        }
    }
}
