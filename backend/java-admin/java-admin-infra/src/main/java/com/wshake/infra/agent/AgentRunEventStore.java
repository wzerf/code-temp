package com.wshake.infra.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentControlModels.AgentRunEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Flux;

/** Redis 持久化运行事件，供断线 SSE 重放和跨副本续接。 */
final class AgentRunEventStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RedissonClient redissonClient;

    AgentRunEventStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    void append(String runKey, AgentRunEvent event, Duration ttl) {
        RAtomicLong sequence = redissonClient.getAtomicLong(sequenceKey(runKey));
        long next = sequence.incrementAndGet();
        RList<String> events = redissonClient.getList(eventsKey(runKey));
        events.add(write(new StoredEvent(next, event)));
        events.expire(ttl);
        sequence.expire(ttl);
        redissonClient.getTopic(eventsTopicKey(runKey)).publish(write(new StoredEvent(next, event)));
    }

    void appendAndUpdateState(String runKey, AgentRunEvent event, RBucket<String> state, Duration ttl) {
        append(runKey, event, ttl);
        if (isTerminal(event.type())) {
            state.set(event.type(), ttl);
        }
    }

    void refresh(String runKey, Duration ttl) {
        redissonClient.getList(eventsKey(runKey)).expire(ttl);
        redissonClient.getAtomicLong(sequenceKey(runKey)).expire(ttl);
    }

    Flux<AgentRunEvent> replayAndFollow(String runKey, String state) {
        if (isTerminal(state)) {
            return Flux.fromIterable(readAll(runKey)).map(StoredEvent::event);
        }
        RTopic topic = redissonClient.getTopic(eventsTopicKey(runKey));
        return Flux.<AgentRunEvent>create(sink -> {
            List<StoredEvent> buffered = new ArrayList<>();
            boolean[] replaying = {true};
            long[] lastSequence = {0};
            int listenerId = topic.addListener(String.class, (channel, value) -> {
                StoredEvent event = read(value);
                synchronized (buffered) {
                    if (replaying[0]) {
                        buffered.add(event);
                        return;
                    }
                    if (event.sequence() > lastSequence[0]) {
                        lastSequence[0] = event.sequence();
                        sink.next(event.event());
                        if (isTerminal(event.event().type())) {
                            sink.complete();
                        }
                    }
                }
            });
            List<StoredEvent> snapshot = readAll(runKey);
            synchronized (buffered) {
                lastSequence[0] = emit(sink, snapshot, lastSequence[0]);
                lastSequence[0] = emit(sink, buffered, lastSequence[0]);
                buffered.clear();
                replaying[0] = false;
            }
            sink.onDispose(() -> topic.removeListener(listenerId));
        });
    }

    CompletableFuture<AgentRunEvent> awaitTerminal(String runKey) {
        AgentRunEvent last = lastEvent(runKey);
        if (last != null && isTerminal(last.type())) {
            return CompletableFuture.completedFuture(last);
        }
        CompletableFuture<AgentRunEvent> result = new CompletableFuture<>();
        RTopic topic = redissonClient.getTopic(eventsTopicKey(runKey));
        int listenerId = topic.addListener(String.class, (channel, value) -> {
            StoredEvent event = read(value);
            if (isTerminal(event.event().type())) {
                result.complete(event.event());
            }
        });
        AgentRunEvent current = lastEvent(runKey);
        if (current != null && isTerminal(current.type())) {
            result.complete(current);
        }
        result.whenComplete((ignored, error) -> topic.removeListener(listenerId))
                .exceptionally(error -> null);
        return result;
    }

    AgentRunEvent lastEvent(String runKey) {
        List<StoredEvent> events = readAll(runKey);
        return events.isEmpty() ? null : events.getLast().event();
    }

    private List<StoredEvent> readAll(String runKey) {
        return redissonClient.<String>getList(eventsKey(runKey)).readAll().stream()
                .map(AgentRunEventStore::read)
                .toList();
    }

    private static String write(StoredEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run event serialization failed");
        }
    }

    private static StoredEvent read(String value) {
        try {
            return MAPPER.readValue(value, StoredEvent.class);
        } catch (JsonProcessingException exception) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "agent run event is invalid");
        }
    }

    private static long emit(
            reactor.core.publisher.FluxSink<AgentRunEvent> sink, List<StoredEvent> events, long lastSequence) {
        long emitted = lastSequence;
        for (StoredEvent event : events) {
            if (event != null && event.sequence() > emitted) {
                emitted = event.sequence();
                sink.next(event.event());
                if (isTerminal(event.event().type())) {
                    sink.complete();
                    break;
                }
            }
        }
        return emitted;
    }

    private static boolean isTerminal(String type) {
        return "COMPLETED".equals(type) || "CANCELLED".equals(type) || "FAILED".equals(type) || "CONFLICT".equals(type);
    }

    private static String eventsKey(String runKey) {
        return runKey + ":events";
    }

    private static String sequenceKey(String runKey) {
        return runKey + ":sequence";
    }

    private static String eventsTopicKey(String runKey) {
        return runKey + ":events:topic";
    }

    private record StoredEvent(long sequence, AgentRunEvent event) {}
}
