package com.wshake.infra.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import com.wshake.service.agent.AgentControlModels.AgentSessionMessageView;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndSetArgs;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.StringCodec;

class AgentRuntimeServiceTest {
    private static final String RUN_KEY = "agent:runtime:request:20:request-1";

    @Test
    void cancel_waitsForPersistedTerminalEvent() throws Exception {
        AgentRuntimeProperties properties = configuredProperties();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> state = mock(RBucket.class);
        RList<String> events = mock(RList.class);
        RAtomicLong sequence = mock(RAtomicLong.class);
        RTopic eventsTopic = mock(RTopic.class);
        RTopic cancelTopic = mock(RTopic.class);
        when(redissonClient.<String>getBucket(RUN_KEY, StringCodec.INSTANCE)).thenReturn(state);
        when(redissonClient.<String>getList(RUN_KEY + ":events", StringCodec.INSTANCE))
                .thenReturn(events);
        when(redissonClient.getAtomicLong(RUN_KEY + ":sequence")).thenReturn(sequence);
        when(redissonClient.getTopic(RUN_KEY + ":events:topic", StringCodec.INSTANCE))
                .thenReturn(eventsTopic);
        when(events.readAll()).thenReturn(java.util.List.of());
        when(redissonClient.getTopic(RUN_KEY + ":cancel", StringCodec.INSTANCE)).thenReturn(cancelTopic);
        ArgumentCaptor<MessageListener<String>> eventListener = ArgumentCaptor.forClass(MessageListener.class);
        when(eventsTopic.addListener(eq(String.class), eventListener.capture())).thenReturn(1);
        when(state.compareAndSet(any(CompareAndSetArgs.class))).thenReturn(true);
        when(sequence.incrementAndGet()).thenReturn(1L);
        doAnswer(invocation -> {
                    String payload = new ObjectMapper()
                            .writeValueAsString(new StoredEvent(
                                    1, new AgentRunEvent("CANCELLED", "request-1", 20L, 10L, null, null, null)));
                    eventListener.getValue().onMessage(RUN_KEY + ":events:topic", payload);
                    return 1L;
                })
                .when(cancelTopic)
                .publish("cancel");

        AgentRuntimeService service = new AgentRuntimeService(properties, redissonClient, mock(), mock());

        AgentRunEvent result = service.cancel(20L, "request-1");

        assertThat(result.type()).isEqualTo("CANCELLED");
    }

    @Test
    void replayAndFollow_preservesEventsPublishedDuringSnapshotRead() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RList<String> events = mock(RList.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.<String>getList(RUN_KEY + ":events", StringCodec.INSTANCE))
                .thenReturn(events);
        when(redissonClient.getTopic(RUN_KEY + ":events:topic", StringCodec.INSTANCE))
                .thenReturn(topic);
        ArgumentCaptor<MessageListener<String>> listener = ArgumentCaptor.forClass(MessageListener.class);
        when(topic.addListener(eq(String.class), listener.capture())).thenReturn(1);
        ObjectMapper mapper = new ObjectMapper();
        String first = mapper.writeValueAsString(
                new StoredEvent(1, new AgentRunEvent("TEXT_DELTA", "request-1", 20L, 10L, "first", null, null)));
        String second = mapper.writeValueAsString(
                new StoredEvent(2, new AgentRunEvent("TEXT_DELTA", "request-1", 20L, 10L, "second", null, null)));
        AtomicBoolean snapshotRead = new AtomicBoolean();
        when(events.readAll()).thenAnswer(invocation -> {
            if (snapshotRead.compareAndSet(false, true)) {
                listener.getValue().onMessage(RUN_KEY + ":events:topic", second);
            }
            return List.of(first);
        });

        List<AgentRunEvent> replayed = new AgentRunEventStore(redissonClient)
                .replayAndFollow(RUN_KEY, "RUNNING")
                .take(2)
                .collectList()
                .block();

        assertThat(replayed).extracting(AgentRunEvent::text).containsExactly("first", "second");
    }

    @Test
    void run_rejectsUnavailableMemoryOrCompressionPolicy() {
        AgentRuntimeProperties properties = configuredProperties();
        AgentRuntimeService service = new AgentRuntimeService(properties, mock(), mock(), mock());
        var plan = new com.wshake.service.agent.AgentControlModels.AgentRunPlan(
                20L, 1L, 10L, 7L, "prompt", null, null, java.util.Map.of("enabled", true), null);

        assertThatThrownBy(() -> service.run(plan, "request-1", "message"))
                .hasMessageContaining("memoryPolicy and compressionPolicy are not enabled");
    }

    @Test
    void terminalEvent_isPersistedBeforeStateBecomesTerminal() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong sequence = mock(RAtomicLong.class);
        RList<String> events = mock(RList.class);
        RTopic topic = mock(RTopic.class);
        RBucket<String> state = mock(RBucket.class);
        when(redissonClient.getAtomicLong(RUN_KEY + ":sequence")).thenReturn(sequence);
        when(redissonClient.<String>getList(RUN_KEY + ":events", StringCodec.INSTANCE))
                .thenReturn(events);
        when(redissonClient.getTopic(RUN_KEY + ":events:topic", StringCodec.INSTANCE))
                .thenReturn(topic);
        when(sequence.incrementAndGet()).thenReturn(1L);

        new AgentRunEventStore(redissonClient)
                .appendAndUpdateState(
                        RUN_KEY,
                        new AgentRunEvent("CANCELLED", "request-1", 20L, 10L, null, null, null),
                        state,
                        Duration.ofMinutes(1));

        InOrder order = inOrder(events, state);
        order.verify(events).add(any());
        order.verify(state).set("CANCELLED", Duration.ofMinutes(1));
    }

    @Test
    void run_rejectsExpiredRequestIdWithoutExecutingAgain() {
        AgentRuntimeProperties properties = configuredProperties();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> consumed = mock(RBucket.class);
        RBucket<String> owner = mock(RBucket.class);
        RBucket<String> state = mock(RBucket.class);
        when(redissonClient.<String>getBucket(RUN_KEY + ":consumed", StringCodec.INSTANCE))
                .thenReturn(consumed);
        when(redissonClient.<String>getBucket(RUN_KEY + ":owner", StringCodec.INSTANCE))
                .thenReturn(owner);
        when(redissonClient.<String>getBucket(RUN_KEY, StringCodec.INSTANCE)).thenReturn(state);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(
                        eq(RScript.Mode.READ_WRITE),
                        any(),
                        eq(RScript.ReturnType.BOOLEAN),
                        anyList(),
                        any(),
                        any(),
                        any()))
                .thenReturn(false);
        when(state.get()).thenReturn(null);
        when(owner.isExists()).thenReturn(false);
        AgentRuntimeService service = new AgentRuntimeService(properties, redissonClient, mock(), mock());

        assertThatThrownBy(() -> service.run(plan(), "request-1", "message").blockFirst())
                .hasMessageContaining("expired and cannot be resumed");
        verify(script)
                .eval(
                        eq(RScript.Mode.READ_WRITE),
                        any(),
                        eq(RScript.ReturnType.BOOLEAN),
                        anyList(),
                        eq("PENDING"),
                        eq("2000"),
                        eq("600000"));
    }

    @Test
    void history_readsAgentScopeRedisStateForCurrentUserSession() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RSet<String> stateKeys = mock(RSet.class);
        RBucket<String> state = mock(RBucket.class);
        String stateKey = "agent:runtime:state:7/20:agent_state";
        when(redissonClient.<String>getSet("agent:runtime:state:7/20:_keys", StringCodec.INSTANCE))
                .thenReturn(stateKeys);
        when(stateKeys.contains("agent_state")).thenReturn(true);
        when(redissonClient.<String>getBucket(stateKey, StringCodec.INSTANCE)).thenReturn(state);
        when(state.get())
                .thenReturn(AgentState.builder()
                        .userId("7")
                        .sessionId("20")
                        .context(List.of(
                                new UserMessage("问题"),
                                new AssistantMessage(
                                        TextBlock.builder().text("答案").build(),
                                        ThinkingBlock.builder().thinking("推理").build())))
                        .build()
                        .toJson());
        AgentRuntimeService service = new AgentRuntimeService(configuredProperties(), redissonClient, mock(), mock());

        assertThat(service.hasSessionHistory(20L, 7L)).isTrue();
        assertThat(service.getSessionHistory(20L, 7L))
                .extracting(
                        AgentSessionMessageView::role,
                        AgentSessionMessageView::content,
                        AgentSessionMessageView::thinking)
                .containsExactly(tuple("user", "问题", null), tuple("ai", "答案", "推理"));
    }

    private static AgentRuntimeProperties configuredProperties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setEnabled(true);
        properties.setModelName("test-model");
        properties.setBaseUrl("https://example.test/v1");
        properties.setApiKey("test-key");
        properties.setExecutionLease(Duration.ofSeconds(2));
        properties.setModelTimeout(Duration.ofMillis(300));
        return properties;
    }

    private static com.wshake.service.agent.AgentControlModels.AgentRunPlan plan() {
        return new com.wshake.service.agent.AgentControlModels.AgentRunPlan(
                20L, 1L, 10L, 7L, "prompt", null, null, null, null);
    }

    private record StoredEvent(long sequence, AgentRunEvent event) {}
}
