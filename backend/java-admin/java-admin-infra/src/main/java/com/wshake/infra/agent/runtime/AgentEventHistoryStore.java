package com.wshake.infra.agent.runtime;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Agent 会话 AG-UI 事件历史存储（Redis list）。
 *
 * <p>供对话页「断点恢复 / 历史回放」：运行面每输出一条 AG-UI 事件即追加到
 * {@code agent:runtime:events:{sessionId}} 列表，前端重进会话时按序拉取并
 * 用同一套事件映射重建消息，保证实时流与历史回放渲染一致。
 *
 * <p>只保留最近 {@link #MAX_EVENTS_PER_SESSION} 条（LTRIM），并设 TTL；
 * 事件内容为去掉 SSE 前导空格后的 AG-UI 事件 JSON（与线上 data 一致）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentEventHistoryStore {

    private static final String KEY_PREFIX = "agent:runtime:events:";

    /** 每会话最多保留的事件条数（防单会话无限增长）。 */
    private static final long MAX_EVENTS_PER_SESSION = 2000;

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /** 追加一条 AG-UI 事件 JSON（右端入列，超限裁掉最旧）；pipeline 合并减少往返。 */
    public void append(Long sessionId, String eventJson) {
        String key = key(sessionId);
        redisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public <K, V> Object execute(org.springframework.data.redis.core.RedisOperations<K, V> ops) {
                ops.opsForList().rightPush((K) key, (V) eventJson);
                ops.opsForList().trim((K) key, -MAX_EVENTS_PER_SESSION, -1);
                ops.expire((K) key, TTL);
                return null;
            }
        });
    }

    /** 按时间顺序返回该会话已持久化的 AG-UI 事件 JSON 列表（空则返回空表）。 */
    public List<String> list(Long sessionId) {
        return redisTemplate.opsForList().range(key(sessionId), 0, -1);
    }

    private String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
