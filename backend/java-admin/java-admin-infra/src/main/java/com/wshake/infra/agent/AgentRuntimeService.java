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
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
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
import org.redisson.client.codec.StringCodec;
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
    private static final String AGENT_ID_PREFIX = "agent-revision-";
    private static final String ALLOWED_TOOLS_KEY = "allowedTools";
    private static final String PLATFORM_TIME_TOOL = "get_platform_time";
    private static final String MODEL_CONFIG_KEY = "model";
    private static final String CANCEL_SIGNAL = "cancel";
    private static final String STATE_STARTING = "STARTING";
    private static final String STATE_RUNNING = "RUNNING";
    private static final String STATE_CANCELLING = "CANCELLING";
    private static final String STATE_CANCELLED = "CANCELLED";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";
    private static final String STATE_CONFLICT = "CONFLICT";
    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_RECOVERING = "RECOVERING";
    private static final String EVENT_STARTED = "STARTED";
    private static final String EVENT_TEXT_DELTA = "TEXT_DELTA";
    private static final String EVENT_TOOL_STARTED = "TOOL_STARTED";
    private static final String EVENT_TOOL_COMPLETED = "TOOL_COMPLETED";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 65_535;
    private static final int MAX_EXECUTION_ATTEMPTS = 1;
    private static final int MAX_AGENT_ITERATIONS = 2;
    private static final int HEARTBEAT_DIVISOR = 2;
    private static final int NO_LISTENER_ID = -1;
    private static final long LOCK_WAIT_MILLIS = 0;
    private static final long LOCK_WATCHDOG_LEASE_MILLIS = -1;

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
        RBucket<String> state = redissonClient.getBucket(runKey, StringCodec.INSTANCE);
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
        return events.replayAndFollow(runKey, STATE_STARTING);
    }

    /** 向固定会话的 AgentScope RuntimeContext 传播取消请求，并等待运行终态。 */
    @Override
    public AgentRunEvent cancel(Long sessionId, String requestId) {
        requireEnabled();
        properties.validate();
        String runKey = runKey(sessionId, requestId);
        RBucket<String> state = redissonClient.getBucket(runKey, StringCodec.INSTANCE);
        AgentRunEventStore events = new AgentRunEventStore(redissonClient);
        var terminal = events.awaitTerminal(runKey);
        if (!markCancelling(state)) {
            terminal.cancel(false);
            throw BizException.of(ResultCode.PARAM_INVALID, "agent run is not running");
        }
        redissonClient.getTopic(cancelTopicKey(runKey), StringCodec.INSTANCE).publish(CANCEL_SIGNAL);
        try {
            return terminal.get(properties.getExecutionLease().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run cancellation interrupted");
        } catch (Exception exception) {
            return new AgentRunEvent(
                    STATE_CANCELLING,
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
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw BizException.of(ResultCode.PARAM_INVALID, "requestId is required");
        }
        String runKey = runKey(sessionId, requestId);
        RBucket<String> state = redissonClient.getBucket(runKey, StringCodec.INSTANCE);
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
            RBucket<String> owner = redissonClient.getBucket(ownerKey(runKey), StringCodec.INSTANCE);
            SessionLock lock;
            try {
                lock = acquireSessionLock(plan.sessionId());
            } catch (RuntimeException exception) {
                owner.delete();
                return Flux.just(event(STATE_FAILED, plan, requestId, null, null, "agent run lock is unavailable"));
            }
            if (lock == null) {
                owner.delete();
                String type = STATE_CANCELLING.equals(state.get()) ? STATE_CANCELLED : STATE_CONFLICT;
                return Flux.just(event(type, plan, requestId, null, null, "session is already running"));
            }
            if (!state.compareAndSet(STATE_STARTING, STATE_RUNNING) && STATE_CANCELLING.equals(state.get())) {
                owner.delete();
                return Flux.just(event(STATE_CANCELLED, plan, requestId, null, null, null))
                        .doFinally(ignored -> lock.release());
            }
            RTopic cancelTopic = redissonClient.getTopic(cancelTopicKey(runKey), StringCodec.INSTANCE);
            Disposable[] heartbeat = new Disposable[1];
            int[] cancelListenerIdHolder = {NO_LISTENER_ID};
            try {
                RuntimeContext context = context(plan);
                return Flux.using(
                        () -> buildAgent(plan),
                        agent -> {
                            cancelListenerIdHolder[0] = cancelTopic.addListener(String.class, (channel, signal) -> {
                                if (CANCEL_SIGNAL.equals(signal)) {
                                    agent.getDelegate().interrupt(context);
                                }
                            });
                            owner.set(Long.toString(lock.ownerId()), properties.getExecutionLease());
                            heartbeat[0] = Flux.interval(
                                            properties.getRequestIdTtl().dividedBy(HEARTBEAT_DIVISOR))
                                    .subscribe(ignored -> {
                                        state.expire(properties.getRequestIdTtl());
                                        owner.set(Long.toString(lock.ownerId()), properties.getExecutionLease());
                                        events.refresh(runKey, properties.getRequestIdTtl());
                                    });
                            if (STATE_CANCELLING.equals(state.get())) {
                                agent.getDelegate().interrupt(context);
                            }
                            int registeredListenerId = cancelListenerIdHolder[0];
                            return Flux.concat(
                                            Flux.just(event(EVENT_STARTED, plan, requestId, null, null, null)),
                                            agent.streamEvents(message, context)
                                                    .concatMap(event -> toEvent(event, plan, requestId, state)))
                                    .onErrorResume(error -> Flux.just(event(
                                            STATE_CANCELLING.equals(state.get()) ? STATE_CANCELLED : STATE_FAILED,
                                            plan,
                                            requestId,
                                            null,
                                            null,
                                            "agent run failed")))
                                    .doFinally(ignored -> releaseRunResources(
                                            owner, heartbeat[0], cancelTopic, registeredListenerId, lock));
                        },
                        agent -> {
                            try {
                                agent.close();
                            } catch (Exception ignored) {
                            }
                        });
            } catch (RuntimeException exception) {
                releaseRunResources(owner, heartbeat[0], cancelTopic, cancelListenerIdHolder[0], lock);
                String type = STATE_CANCELLING.equals(state.get()) ? STATE_CANCELLED : STATE_FAILED;
                return Flux.just(event(type, plan, requestId, null, null, "agent run initialization failed"));
            }
        });
    }

    private HarnessAgent buildAgent(AgentRunPlan plan) {
        Toolkit toolkit = new Toolkit();
        if (allowsPlatformTimeTool(plan)) {
            toolkit.registerTool(runtimeTools);
        }
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .stream(true)
                .httpTransport(OkHttpTransport.builder()
                        .config(HttpTransportConfig.defaults())
                        .build());
        // xAI/Grok 仅允许 user 消息带 name；默认 OpenAI formatter 会给 assistant/system 写入 name
        if (GrokChatFormatter.needsGrokFormatter(properties.getModelName(), properties.getBaseUrl())) {
            modelBuilder.formatter(new GrokChatFormatter());
        }
        OpenAIChatModel model = modelBuilder.build();
        return HarnessAgent.builder()
                .agentId(AGENT_ID_PREFIX + plan.agentRevisionId())
                .name(AGENT_ID_PREFIX + plan.agentRevisionId())
                .sysPrompt(plan.systemPrompt())
                .model(model)
                .toolkit(toolkit)
                .stateStore(RedisAgentStateStore.builder()
                        .keyPrefix(KEY_PREFIX + "state:")
                        .redissonClient(redissonClient)
                        .build())
                .modelExecutionConfig(ExecutionConfig.builder()
                        .timeout(properties.getModelTimeout())
                        .maxAttempts(MAX_EXECUTION_ATTEMPTS)
                        .build())
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(properties.getModelTimeout())
                        .maxAttempts(MAX_EXECUTION_ATTEMPTS)
                        .build())
                .maxIters(MAX_AGENT_ITERATIONS)
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
            return Flux.just(event(EVENT_TEXT_DELTA, plan, requestId, delta.getDelta(), null, null));
        }
        if (event instanceof ToolCallStartEvent toolCall) {
            return Flux.just(event(EVENT_TOOL_STARTED, plan, requestId, null, toolCall.getToolCallName(), null));
        }
        if (event instanceof ToolResultEndEvent toolResult) {
            return Flux.just(event(
                    EVENT_TOOL_COMPLETED,
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
            return Flux.just(event(STATE_CANCELLED, plan, requestId, null, null, stop.getReason()));
        }
        if (event instanceof AgentEndEvent) {
            String current = state.get();
            String type = (STATE_CANCELLING.equals(current) || STATE_CANCELLED.equals(current))
                    ? STATE_CANCELLED
                    : STATE_COMPLETED;
            return Flux.just(event(type, plan, requestId, null, null, null));
        }
        return Flux.empty();
    }

    private boolean initializeRun(String runKey) {
        return redissonClient
                .getScript(StringCodec.INSTANCE)
                .eval(
                        RScript.Mode.READ_WRITE,
                        "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                                + "redis.call('SET', KEYS[1], 'USED') "
                                + "redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]) "
                                + "redis.call('SET', KEYS[3], '" + STATE_STARTING + "', 'PX', ARGV[3]) return 1",
                        RScript.ReturnType.BOOLEAN,
                        List.of(consumedKey(runKey), ownerKey(runKey), runKey),
                        STATE_PENDING,
                        Long.toString(properties.getExecutionLease().toMillis()),
                        Long.toString(properties.getRequestIdTtl().toMillis()));
    }

    private SessionLock acquireSessionLock(Long sessionId) {
        long ownerId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        RLock lock = redissonClient.getLock(sessionLockKey(sessionId));
        try {
            boolean acquired = lock.tryLockAsync(
                            LOCK_WAIT_MILLIS, LOCK_WATCHDOG_LEASE_MILLIS, TimeUnit.MILLISECONDS, ownerId)
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
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw BizException.of(ResultCode.PARAM_INVALID, "requestId is required");
        }
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw BizException.of(ResultCode.PARAM_INVALID, "message is required");
        }
        Object configuredModel =
                plan.modelConfig() == null ? null : plan.modelConfig().get(MODEL_CONFIG_KEY);
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
        return state.compareAndSet(CompareAndSetArgs.expected(STATE_RUNNING).set(STATE_CANCELLING))
                || state.compareAndSet(
                        CompareAndSetArgs.expected(STATE_STARTING).set(STATE_CANCELLING));
    }

    private static boolean allowsPlatformTimeTool(AgentRunPlan plan) {
        Object allowedTools =
                plan.permissionPolicy() == null ? null : plan.permissionPolicy().get(ALLOWED_TOOLS_KEY);
        if (!(allowedTools instanceof Iterable<?> values)) {
            return false;
        }
        for (Object value : values) {
            if (PLATFORM_TIME_TOOL.equals(value)) {
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
        RBucket<String> owner = redissonClient.getBucket(ownerKey(runKey), StringCodec.INSTANCE);
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
            if (!owner.setIfAbsent(STATE_RECOVERING, properties.getExecutionLease())) {
                String recovered = state.get();
                return recovered == null ? STATE_STARTING : recovered;
            }
            if (state.get() != null && !current.equals(state.get())) {
                return state.get();
            }
            events.appendAndUpdateState(
                    runKey,
                    new AgentRunEvent(
                            STATE_FAILED,
                            requestId,
                            sessionId,
                            agentRevisionId,
                            null,
                            null,
                            "agent run owner is unavailable"),
                    state,
                    properties.getRequestIdTtl());
            return STATE_FAILED;
        } finally {
            owner.delete();
            recoveryLock.release();
        }
    }

    private static boolean isTerminal(String state) {
        return STATE_COMPLETED.equals(state)
                || STATE_CANCELLED.equals(state)
                || STATE_FAILED.equals(state)
                || STATE_CONFLICT.equals(state);
    }

    private static void releaseRunResources(
            RBucket<String> owner, Disposable heartbeat, RTopic cancelTopic, int cancelListenerId, SessionLock lock) {
        if (heartbeat != null) {
            heartbeat.dispose();
        }
        owner.delete();
        if (cancelListenerId > NO_LISTENER_ID) {
            cancelTopic.removeListener(cancelListenerId);
        }
        lock.release();
    }

    private static String runKey(Long sessionId, String requestId) {
        return KEY_PREFIX + "request:" + sessionId + ":" + requestId;
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
