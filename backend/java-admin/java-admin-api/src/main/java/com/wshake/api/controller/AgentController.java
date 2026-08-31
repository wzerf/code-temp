package com.wshake.api.controller;

import com.wshake.api.dto.AgentMessageRequest;
import com.wshake.api.dto.CancelAgentRunRequest;
import com.wshake.api.dto.CreateAgentRequest;
import com.wshake.api.dto.CreateAgentRevisionRequest;
import com.wshake.api.dto.RollbackAgentRevisionRequest;
import com.wshake.api.dto.UpdateAgentRevisionRequest;
import com.wshake.api.vo.AgentDefinitionVO;
import com.wshake.api.vo.AgentRevisionVO;
import com.wshake.api.vo.AgentRunEventVO;
import com.wshake.api.vo.AgentSessionHistoryVO;
import com.wshake.api.vo.AgentSessionVO;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.Result;
import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import com.wshake.service.agent.AgentControlModels.CreateAgentCommand;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.agent.AgentControlService;
import com.wshake.service.agent.AgentRuntimeGateway;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/** Agent 控制面：管理草稿/发布 Revision，并将会话固定到已发布 Revision。 */
@Tag(name = "Agent 管理", description = "Definition、草稿/发布 Revision 与会话固定版本")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentControlService agentControlService;
    private final Converter converter;
    private final AgentRuntimeGateway agentRuntimeGateway;

    @PostMapping
    @Operation(summary = "创建 Agent 与首个草稿 Revision")
    public Result<AgentRevisionVO> create(@Valid @RequestBody CreateAgentRequest request) {
        CreateAgentCommand body = converter.convert(request, CreateAgentCommand.class);
        CreateAgentCommand command = new CreateAgentCommand(
                RequestContext.requireUserId(),
                body.name(),
                body.description(),
                body.systemPrompt(),
                body.modelConfig(),
                body.permissionPolicy(),
                body.memoryPolicy(),
                body.compressionPolicy(),
                body.remark());
        return Result.ok(converter.convert(agentControlService.createAgent(command), AgentRevisionVO.class));
    }

    @GetMapping
    @Operation(summary = "列出当前用户的 Agent Definition")
    public Result<java.util.List<AgentDefinitionVO>> listDefinitions() {
        return Result.ok(converter.convert(
                agentControlService.listDefinitions(RequestContext.requireUserId()), AgentDefinitionVO.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 Agent Definition")
    public Result<AgentDefinitionVO> getDefinition(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.getDefinition(id, RequestContext.requireUserId()), AgentDefinitionVO.class));
    }

    @PostMapping("/{id}/revisions")
    @Operation(summary = "创建草稿 Revision")
    public Result<AgentRevisionVO> createDraft(
            @PathVariable Long id, @Valid @RequestBody CreateAgentRevisionRequest request) {
        CreateRevisionCommand body = converter.convert(request, CreateRevisionCommand.class);
        CreateRevisionCommand command = new CreateRevisionCommand(
                id,
                body.systemPrompt(),
                body.modelConfig(),
                body.permissionPolicy(),
                body.memoryPolicy(),
                body.compressionPolicy(),
                body.remark());
        return Result.ok(converter.convert(
                agentControlService.createDraft(command, RequestContext.requireUserId()), AgentRevisionVO.class));
    }

    @GetMapping("/revisions/{id}")
    @Operation(summary = "获取 Revision")
    public Result<AgentRevisionVO> getRevision(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.getRevision(id, RequestContext.requireUserId()), AgentRevisionVO.class));
    }

    @PutMapping("/revisions/{id}")
    @Operation(summary = "更新草稿 Revision")
    public Result<AgentRevisionVO> updateDraft(
            @PathVariable Long id, @RequestBody(required = false) UpdateAgentRevisionRequest body) {
        UpdateAgentRevisionRequest request = body == null ? new UpdateAgentRevisionRequest() : body;
        UpdateRevisionCommand command = new UpdateRevisionCommand(
                id,
                request.getSystemPrompt(),
                request.isSystemPromptPresent(),
                request.getModelConfig(),
                request.isModelConfigPresent(),
                request.getPermissionPolicy(),
                request.isPermissionPolicyPresent(),
                request.getMemoryPolicy(),
                request.isMemoryPolicyPresent(),
                request.getCompressionPolicy(),
                request.isCompressionPolicyPresent(),
                request.getRemark(),
                request.isRemarkPresent());
        return Result.ok(converter.convert(
                agentControlService.updateDraft(command, RequestContext.requireUserId()), AgentRevisionVO.class));
    }

    @PostMapping("/revisions/{id}/publish")
    @Operation(summary = "发布草稿 Revision")
    public Result<AgentRevisionVO> publish(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.publish(id, RequestContext.requireUserId()), AgentRevisionVO.class));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "切换后续新会话的发布 Revision")
    public Result<AgentDefinitionVO> rollback(
            @PathVariable Long id, @Valid @RequestBody RollbackAgentRevisionRequest request) {
        return Result.ok(converter.convert(
                agentControlService.rollback(id, request.getRevisionId(), RequestContext.requireUserId()),
                AgentDefinitionVO.class));
    }

    @PostMapping("/{id}/sessions")
    @Operation(summary = "创建尚未启动的会话")
    public Result<AgentSessionVO> createSession(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.createSession(id, RequestContext.requireUserId()), AgentSessionVO.class));
    }

    @GetMapping("/{id}/sessions")
    @Operation(summary = "列出当前用户的 Agent 历史会话")
    public Result<java.util.List<AgentSessionVO>> listSessions(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.listSessions(id, RequestContext.requireUserId()), AgentSessionVO.class));
    }

    @PostMapping("/sessions/{id}/resolve-revision")
    @Operation(summary = "首次运行时固定当前已发布 Revision")
    public Result<AgentSessionVO> resolveSessionRevision(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.resolveSessionRevision(id, RequestContext.requireUserId()), AgentSessionVO.class));
    }

    @PostMapping(value = "/sessions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息并接收 Agent SSE 事件")
    public SseEmitter run(@PathVariable Long id, @Valid @RequestBody AgentMessageRequest request) {
        var plan = agentControlService.prepareRun(id, RequestContext.requireUserId());
        agentControlService.touchSession(id, RequestContext.requireUserId());
        SseEmitter emitter = new SseEmitter(0L);
        Disposable subscription = agentRuntimeGateway
                .run(plan, request.getRequestId(), request.getMessage())
                .subscribe(event -> send(emitter, event), emitter::completeWithError, emitter::complete);
        bindSubscription(emitter, subscription);
        return emitter;
    }

    @GetMapping(value = "/sessions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "续接 Agent SSE 事件")
    public SseEmitter resume(@PathVariable Long id, @RequestParam @NotBlank @Size(max = 128) String requestId) {
        agentControlService.getSession(id, RequestContext.requireUserId());
        SseEmitter emitter = new SseEmitter(0L);
        Disposable subscription = agentRuntimeGateway
                .resume(id, requestId)
                .subscribe(event -> send(emitter, event), emitter::completeWithError, emitter::complete);
        bindSubscription(emitter, subscription);
        return emitter;
    }

    @PostMapping("/sessions/{id}/cancel")
    @Operation(summary = "取消正在运行的 Agent 请求")
    public Result<AgentRunEventVO> cancel(@PathVariable Long id, @Valid @RequestBody CancelAgentRunRequest request) {
        agentControlService.getSession(id, RequestContext.requireUserId());
        return Result.ok(
                converter.convert(agentRuntimeGateway.cancel(id, request.getRequestId()), AgentRunEventVO.class));
    }

    @PostMapping("/{id}/emergency-disable")
    @Operation(summary = "紧急禁用 Agent 的新会话与首次运行入口")
    public Result<AgentDefinitionVO> emergencyDisable(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.emergencyDisable(id, RequestContext.requireUserId()), AgentDefinitionVO.class));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "获取当前用户的 Agent 会话")
    public Result<AgentSessionVO> getSession(@PathVariable Long id) {
        return Result.ok(converter.convert(
                agentControlService.getSession(id, RequestContext.requireUserId()), AgentSessionVO.class));
    }

    @GetMapping("/sessions/{id}/history")
    @Operation(summary = "获取当前用户的 Agent 会话历史")
    public Result<AgentSessionHistoryVO> getSessionHistory(@PathVariable Long id) {
        var history = agentControlService.getSessionHistory(id, RequestContext.requireUserId());
        return Result.ok(new AgentSessionHistoryVO(
                converter.convert(history.session(), AgentSessionVO.class),
                history.messages().stream()
                        .map(item -> new AgentSessionHistoryVO.MessageVO(
                                item.id(), item.role(), item.content(), item.thinking(), item.createdAt()))
                        .toList()));
    }

    private static void bindSubscription(SseEmitter emitter, Disposable subscription) {
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(ignored -> subscription.dispose());
    }

    private void send(SseEmitter emitter, AgentRunEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(converter.convert(event, AgentRunEventVO.class)));
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }
}
