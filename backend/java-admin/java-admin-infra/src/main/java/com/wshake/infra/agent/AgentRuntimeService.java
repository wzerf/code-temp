package com.wshake.infra.agent;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import com.wshake.service.agent.AgentControlModels.AgentRunPlan;
import com.wshake.service.agent.AgentRuntimeGateway;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.redis.state.redisson.RedissonAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndSetArgs;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/** 将固定 Revision 装配为只读、可取消的 AgentScope 运行。 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class AgentRuntimeService implements AgentRuntimeGateway {

    private static final String KEY_PREFIX = "agent:runtime:";

    private final AgentRuntimeProperties properties;
    private final RedissonClient redissonClient;
    private final okhttp3.OkHttpClient okHttpClient;
    private final AgentRuntimeTools runtimeTools;

    /**
     * 启动或重放一次会话请求。后台订阅独立于 SSE 客户端，关闭浏览器连接不等同取消执行。
     */
    @Override
    public Flux<AgentRunEvent> run(AgentRunPlan plan, String requestId, String message) {
        requireEnabled();
        properties.validate();
        validatePlan(plan, requestId, message);
        String runKey = runKey(plan.sessionId(), requestId);
        RBucket<String> state = redissonClient.getBucket(runKey);
        AgentRunEventStore events = new AgentRunEventStore(redissonClient);
        if (!initializeRun(runKey)) {
            String currentState =
                    recoverState(plan.sessionId(), requestId, plan.agentRevisionId(), state, events, runKey);
            if (currentState == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "agent run has expired and cannot be resumed");
            }
            return events.replayAndFollow(runKey, currentState);
        }
        // 后台启动执行，客户端只观察 Redis 事件流
        execute(plan, requestId, message, state, runKey, events)
                .doOnNext(event -> events.appendAndUpdateState(runKey, event, state, properties.getRequestIdTtl()))
                .subscribe();
        return events.replayAndFollow(runKey, "STARTING");
    }

    /** 向固定会话的 AgentScope RuntimeContext 传播取消请求，并等待运行终态。 */
    @Override
    public AgentRunEvent cancel(Long sessionId, String requestId) {
        requireEnabled();
        properties.validate();
        String runKey = runKey(sessionId, requestId);
        RBucket<String> state = redissonClient.getBucket(runKey);
        AgentRunEventStore events = new AgentRunEventStore(redissonClient);
        var terminal = events.awaitTerminal(runKey);
        if (!markCancelling(state)) {
            terminal.cancel(false);
            throw BizException.of(ResultCode.PARAM_INVALID, "agent run is not running");
        }
        redissonClient.getTopic(cancelTopicKey(runKey)).publish("cancel");
        try {
            return terminal.get(properties.getExecutionLease().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run cancellation interrupted");
        } catch (Exception exception) {
            return new AgentRunEvent(
                    "CANCELLING",
                    requestId,
                    sessionId,
                    null,
                    null,
                    null,
                    "agent run cancellation is still in progress");
        }
    }

    /** 重放已有事件，并在运行中持续接收 Redis 发布的后续事件。 */
    @Override
    public Flux<AgentRunEvent> resume(Long sessionId, String requestId) {
        requireEnabled();
        properties.validate();
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "requestId is required");
        }
        String runKey = runKey(sessionId, requestId);
        RBucket<String> state = redissonClient.getBucket(runKey);
        AgentRunEventStore events = new AgentRunEventStore(redissonClient);
        String currentState = recoverState(sessionId, requestId, null, state, events, runKey);
        if (currentState == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent run is not found");
        }
        return events.replayAndFollow(runKey, currentState);
    }

    private Flux<AgentRunEvent> execute(
            AgentRunPlan plan,
            String requestId,
            String message,
            RBucket<String> state,
            String runKey,
            AgentRunEventStore events) {
        return Flux.defer(() -> {
            RBucket<String> owner = redissonClient.getBucket(ownerKey(runKey));
            SessionLock lock;
            try {
                lock = acquireSessionLock(plan.sessionId());
            } catch (RuntimeException exception) {
                owner.delete();
                return Flux.just(event("FAILED", plan, requestId, null, null, "agent run lock is unavailable"));
            }
            if (lock == null) {
                owner.delete();
                String type = "CANCELLING".equals(state.get()) ? "CANCELLED" : "CONFLICT";
                return Flux.just(event(type, plan, requestId, null, null, "session is already running"));
            }
            if (!state.compareAndSet("STARTING", "RUNNING") && "CANCELLING".equals(state.get())) {
                owner.delete();
                return Flux.just(event("CANCELLED", plan, requestId, null, null, null))
                        .doFinally(ignored -> lock.release());
            }
            RTopic cancelTopic = redissonClient.getTopic(cancelTopicKey(runKey));
            Disposable[] heartbeat = new Disposable[1];
            int cancelListenerId = -1;
            try {
                RuntimeContext context = context(plan);
                HarnessAgent agent = agent(plan);
                cancelListenerId = cancelTopic.addListener(String.class, (channel, signal) -> {
                    if ("cancel".equals(signal)) {
                        agent.getDelegate().interrupt(context);
                    }
                });
                owner.set(Long.toString(lock.ownerId()), properties.getExecutionLease());
                heartbeat[0] = Flux.interval(properties.getRequestIdTtl().dividedBy(2))
                        .subscribe(ignored -> {
                            state.expire(properties.getRequestIdTtl());
                            owner.set(Long.toString(lock.ownerId()), properties.getExecutionLease());
                            events.refresh(runKey, properties.getRequestIdTtl());
                        });
                if ("CANCELLING".equals(state.get())) {
                    agent.getDelegate().interrupt(context);
                }
                int registeredListenerId = cancelListenerId;
                return Flux.concat(
                                Flux.just(event("STARTED", plan, requestId, null, null, null)),
                                agent.streamEvents(message, context)
                                        .flatMap(event -> toEvent(event, plan, requestId, state)))
                        .onErrorResume(error -> Flux.just(event(
                                "CANCELLING".equals(state.get()) ? "CANCELLED" : "FAILED",
                                plan,
                                requestId,
                                null,
                                null,
                                "agent run failed")))
                        .doFinally(ignored ->
                                releaseRunResources(owner, heartbeat[0], cancelTopic, registeredListenerId, lock));
            } catch (RuntimeException exception) {
                releaseRunResources(owner, heartbeat[0], cancelTopic, cancelListenerId, lock);
                String type = "CANCELLING".equals(state.get()) ? "CANCELLED" : "FAILED";
                return Flux.just(event(type, plan, requestId, null, null, "agent run initialization failed"));
            }
        });
    }

    private HarnessAgent agent(AgentRunPlan plan) {
        return buildAgent(plan);
    }

    private HarnessAgent buildAgent(AgentRunPlan plan) {
        Toolkit toolkit = new Toolkit();
        if (allowsPlatformTimeTool(plan)) {
            toolkit.registerTool(runtimeTools);
        }
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .stream(true)
                .httpTransport(OkHttpTransport.builder()
                        .client(okHttpClient)
                        .config(HttpTransportConfig.defaults())
                        .build())
                .build();
        return HarnessAgent.builder()
                .agentId("agent-revision-" + plan.agentRevisionId())
                .name("agent-revision-" + plan.agentRevisionId())
                .sysPrompt(plan.systemPrompt())
                .model(model)
                .toolkit(toolkit)
                .stateStore(RedissonAgentStateStore.builder()
                        .keyPrefix(KEY_PREFIX + "state:")
                        .redissonClient(redissonClient)
                        .build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(properties.getModelTimeout())
                        .maxAttempts(1)
                        .build())
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(properties.getModelTimeout())
                        .maxAttempts(1)
                        .build())
                .maxIters(2)
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableToolsConfig()
                .build();
    }

    private Flux<AgentRunEvent> toEvent(AgentEvent event, AgentRunPlan plan, String requestId, RBucket<String> state) {
        if (event instanceof TextBlockDeltaEvent delta) {
            return Flux.just(event("TEXT_DELTA", plan, requestId, delta.getDelta(), null, null));
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            return Flux.just(event("TOOL_STARTED", plan, requestId, null, toolCall.getToolCallName(), null));
        }
        if (event instanceof ToolResultEndEvent toolResult) {
            return Flux.just(event(
                    "TOOL_COMPLETED",
                    plan,
                    requestId,
                    null,
                    toolResult.getToolCallName(),
                    toolResult.getState().name()));
        }
        if (event instanceof RequestStopEvent stop) {
            if (stop.getGenerateReason() != GenerateReason.INTERRUPTED) {
                return Flux.error(
                        BizException.of(ResultCode.PARAM_INVALID, "agent runtime stop reason is not enabled"));
            }
            return Flux.just(event("CANCELLED", plan, requestId, null, null, stop.getReason()));
        }
        if (event instanceof AgentEndEvent) {
            String current = state.get();
            String type = ("CANCELLING".equals(current) || "CANCELLED".equals(current)) ? "CANCELLED" : "COMPLETED";
            return Flux.just(event(type, plan, requestId, null, null, null));
        }
        return Flux.empty();
    }

    private boolean initializeRun(String runKey) {
        return redissonClient
                .getScript()
                .eval(
                        RScript.Mode.READ_WRITE,
                        "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                                + "redis.call('SET', KEYS[1], 'USED') "
                                + "redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]) "
                                + "redis.call('SET', KEYS[3], 'STARTING', 'PX', ARGV[3]) return 1",
                        RScript.ReturnType.BOOLEAN,
                        List.of(consumedKey(runKey), ownerKey(runKey), runKey),
                        "PENDING",
                        properties.getExecutionLease().toMillis(),
                        properties.getRequestIdTtl().toMillis());
    }

    private SessionLock acquireSessionLock(Long sessionId) {
        long ownerId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        RLock lock = redissonClient.getLock(sessionLockKey(sessionId));
        try {
            boolean acquired = lock.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, ownerId)
                    .toCompletableFuture()
                    .get();
            return acquired ? new SessionLock(lock, ownerId) : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run interrupted while acquiring lock");
        } catch (Exception exception) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run lock is unavailable");
        }
    }

    private static RuntimeContext context(AgentRunPlan plan) {
        return RuntimeContext.builder()
                .userId(plan.ownerUserId().toString())
                .sessionId(plan.sessionId().toString())
                .build();
    }

    private void validatePlan(AgentRunPlan plan, String requestId, String message) {
        if (plan == null || plan.agentRevisionId() == null || plan.sessionId() == null || plan.ownerUserId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent run plan is invalid");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "requestId is required");
        }
        if (message == null || message.isBlank() || message.length() > 65535) {
            throw BizException.of(ResultCode.PARAM_INVALID, "message is required");
        }
        Object configuredModel =
                plan.modelConfig() == null ? null : plan.modelConfig().get("model");
        if ((configuredModel != null && !(configuredModel instanceof String))
                || (configuredModel instanceof String configured
                        && !properties.getModelName().equals(configured))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "modelConfig.model is not enabled");
        }
        if (hasEntries(plan.memoryPolicy()) || hasEntries(plan.compressionPolicy())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "memoryPolicy and compressionPolicy are not enabled");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent runtime is disabled");
        }
    }

    private static boolean markCancelling(RBucket<String> state) {
        return state.compareAndSet(CompareAndSetArgs.expected("RUNNING").set("CANCELLING"))
                || state.compareAndSet(CompareAndSetArgs.expected("STARTING").set("CANCELLING"));
    }

    private static boolean allowsPlatformTimeTool(AgentRunPlan plan) {
        Object allowedTools =
                plan.permissionPolicy() == null ? null : plan.permissionPolicy().get("allowedTools");
        if (!(allowedTools instanceof Iterable<?> values)) {
            return false;
        }
        for (Object value : values) {
            if ("get_platform_time".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEntries(Map<String, Object> policy) {
        return policy != null && !policy.isEmpty();
    }

    private String recoverState(
            Long sessionId,
            String requestId,
            Long agentRevisionId,
            RBucket<String> state,
            AgentRunEventStore events,
            String runKey) {
        String current = state.get();
        RBucket<String> owner = redissonClient.getBucket(ownerKey(runKey));
        if (current == null && !owner.isExists()) {
            return null;
        }
        if (isTerminal(current) || owner.isExists()) {
            return current;
        }
        SessionLock recoveryLock = acquireSessionLock(sessionId);
        if (recoveryLock == null) {
            return current;
        }
        try {
            if (!owner.setIfAbsent("RECOVERING", properties.getExecutionLease())) {
                String recovered = state.get();
                return recovered == null ? "STARTING" : recovered;
            }
            if (state.get() != null && !current.equals(state.get())) {
                return state.get();
            }
            events.appendAndUpdateState(
                    runKey,
                    new AgentRunEvent(
                            "FAILED",
                            requestId,
                            sessionId,
                            agentRevisionId,
                            null,
                            null,
                            "agent run owner is unavailable"),
                    state,
                    properties.getRequestIdTtl());
            return "FAILED";
        } finally {
            owner.delete();
            recoveryLock.release();
        }
    }

    private static boolean isTerminal(String state) {
        return "COMPLETED".equals(state)
                || "CANCELLED".equals(state)
                || "FAILED".equals(state)
                || "CONFLICT".equals(state);
    }

    private static void releaseRunResources(
            RBucket<String> owner, Disposable heartbeat, RTopic cancelTopic, int cancelListenerId, SessionLock lock) {
        if (heartbeat != null) {
            heartbeat.dispose();
        }
        owner.delete();
        if (cancelListenerId >= 0) {
            cancelTopic.removeListener(cancelListenerId);
        }
        lock.release();
    }

    private static String runKey(Long sessionId, String requestId) {
        return KEY_PREFIX + "request:{" + sessionId + ":" + requestId + "}";
    }

    private static String sessionLockKey(Long sessionId) {
        return KEY_PREFIX + "lock:" + sessionId;
    }

    private static String cancelTopicKey(String runKey) {
        return runKey + ":cancel";
    }

    private static String consumedKey(String runKey) {
        return runKey + ":consumed";
    }

    private static String ownerKey(String runKey) {
        return runKey + ":owner";
    }

    private static AgentRunEvent event(
            String type, AgentRunPlan plan, String requestId, String text, String toolName, String message) {
        return new AgentRunEvent(type, requestId, plan.sessionId(), plan.agentRevisionId(), text, toolName, message);
    }

    private record SessionLock(RLock lock, long ownerId) {
        void release() {
            lock.unlockAsync(ownerId).toCompletableFuture().join();
        }
    }
}
