package com.wshake.infra.agent.runtime;

import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.event.AguiEvent.RunError;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Agent 对话运行服务：按会话装配 HarnessAgent，并经官方 AG-UI 适配器输出标准事件流。
 *
 * <p>对齐 docs/agent-conversation-architecture.md §3/§6：运行面事件统一为 AG-UI 标准事件
 * （RUN_STARTED / RUN_FINISHED / TEXT_MESSAGE_* / REASONING_MESSAGE_* / TOOL_CALL_*），
 * 由官方 {@link AguiAgentAdapter} 完成 {@code AgentEvent → AG-UI 事件} 转换。平台只负责
 * 会话装配（{@link AgentRunPlanner} + {@link AgentHarnessFactory}）与 SSE 输出骨架
 * （对齐官方 {@code AguiMvcController} 的事件订阅写法：adapter.run 订阅 → emitter 写出，
 * 超时/断连中断 agent）。
 *
 * <p>每次运行新建 HarnessAgent，会话上下文经 RedisAgentStateStore 持久化（按 threadId=
 * 平台 sessionId 隔离），流结束或失败时 close（close 只清内存缓存，不影响 Redis）。
 *
 * @author wshake
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAguiService {

    private final AgentRunPlanner runPlanner;
    private final AgentHarnessFactory harnessFactory;
    private final AgentRunStateStore runStateStore;
    private final AgentRuntimeProperties properties;
    private final Executor agentRunExecutor;

    private final AguiEventEncoder encoder = new AguiEventEncoder();

    /**
     * 异步执行一次 Agent 运行，经 SSE 输出 AG-UI 事件流（data 为 JSON,无 event: 名）。
     *
     * @param sessionId     平台会话 id（= AG-UI threadId）
     * @param requestUserId 当前登录用户（会话归属校验；null 不校验）
     * @param requestRunId  客户端幂等 runId（null 时服务端生成）
     * @param input         AG-UI 标准 RunAgentInput
     * @return SSE emitter（装配/幂等失败时已完成并含错误事件）
     */
    public SseEmitter run(Long sessionId, Long requestUserId, String requestRunId, RunAgentInput input) {
        SseEmitter emitter = new SseEmitter(properties.getRunTimeout().toMillis());
        String runId = requestRunId == null || requestRunId.isBlank()
                ? UUID.randomUUID().toString()
                : requestRunId;
        agentRunExecutor.execute(() -> runOnWorker(sessionId, requestUserId, runId, input, emitter));
        return emitter;
    }

    private void runOnWorker(
            Long sessionId, Long requestUserId, String runId, RunAgentInput input, SseEmitter emitter) {
        // 幂等：同 (sessionId, runId) 已消费 → 不重复执行副作用
        if (!runStateStore.tryStart(sessionId, runId)) {
            sendError(emitter, sessionId, runId, "重复的 runId,该运行已处理");
            emitter.complete();
            return;
        }
        HarnessAgent agent = null;
        try {
            agent = harnessFactory.create(runPlanner.plan(sessionId, requestUserId));
        } catch (Throwable t) {
            log.warn("agent assemble failed session={} cause={}", sessionId, t.toString());
            sendError(emitter, sessionId, runId, "运行准备失败: " + messageOf(t));
            runStateStore.markFailed(sessionId, runId);
            emitter.complete();
            return;
        }
        HarnessAgent finalAgent = agent;
        AtomicBoolean finished = new AtomicBoolean(false);
        try {
            AguiAdapterConfig config = AguiAdapterConfig.builder()
                    .enableReasoning(properties.isEmitReasoning())
                    .emitToolCallArgs(properties.isEmitToolCallArgs())
                    .emitStateEvents(properties.isEmitStateEvents())
                    .emitTokenUsage(properties.isEmitTokenUsage())
                    .runTimeout(properties.getRunTimeout())
                    .build();
            // adapter 自动把 input.threadId → RuntimeContext.sessionId
            Disposable subscription = new AguiAgentAdapter(finalAgent, config)
                    .run(input)
                    .doOnNext(event -> {
                        if (event instanceof RunError) {
                            runStateStore.markFailed(sessionId, runId);
                        }
                    })
                    .subscribe(
                            event -> sendEvent(emitter, event),
                            error -> {
                                log.warn(
                                        "agui stream error session={} run={} cause={}",
                                        sessionId,
                                        runId,
                                        error.toString());
                                sendError(emitter, sessionId, runId, messageOf(error));
                                finish(finalAgent, finished, sessionId, runId, emitter, false);
                            },
                            () -> finish(finalAgent, finished, sessionId, runId, emitter, true));
            // 客户端断开/超时：中断 agent 执行（对齐 docs「取消必须停止执行」）
            emitter.onCompletion(subscription::dispose);
            emitter.onTimeout(() -> {
                log.info("agui sse timeout session={} run={}, interrupting agent", sessionId, runId);
                finalAgent.interrupt();
            });
            emitter.onError(e -> {
                log.info(
                        "agui sse error session={} run={} err={}, interrupting agent",
                        sessionId,
                        runId,
                        e.getMessage());
                finalAgent.interrupt();
            });
        } catch (Throwable t) {
            log.warn("agent agui run failed session={} run={} cause={}", sessionId, runId, t.toString());
            sendError(emitter, sessionId, runId, "运行失败: " + messageOf(t));
            finish(finalAgent, finished, sessionId, runId, emitter, false);
        }
    }

    /** 流终态收尾：状态标记 + close agent + complete emitter（只执行一次）。 */
    private void finish(
            HarnessAgent agent,
            AtomicBoolean finished,
            Long sessionId,
            String runId,
            SseEmitter emitter,
            boolean success) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try (agent) {
            if (success) {
                runStateStore.markFinished(sessionId, runId);
            }
        } catch (Exception ignore) {
            // close 失败不影响结果
        } finally {
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter, AguiEvent event) {
        try {
            String json = encoder.encodeToJson(event);
            emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("SSE 发送失败", e);
        }
    }

    private void sendError(SseEmitter emitter, Long sessionId, String runId, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .data(
                            encoder.encodeToJson(
                                    new RunError(String.valueOf(sessionId), runId, message, null, null, null)),
                            MediaType.APPLICATION_JSON));
        } catch (IOException ignore) {
            // 客户端已断,忽略
        }
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
