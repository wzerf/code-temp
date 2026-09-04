package com.wshake.infra.agent.runtime;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 运行幂等/状态存储（Redis）。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.4：同一 (sessionId, runId) 重试命中
 * {@code consumed} 后不重复执行副作用。键形如
 * {@code agent:runtime:run:{sessionId}:{runId}}，值记录状态机
 * {@code STARTING → FINISHED / FAILED}。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentRunStateStore {

    private static final String STATE_STARTING = "STARTING";
    private static final String STATE_FINISHED = "FINISHED";
    private static final String STATE_FAILED = "FAILED";

    private final StringRedisTemplate redisTemplate;
    private final AgentRuntimeProperties properties;

    private static final Duration TTL = Duration.ofHours(2);

    /**
     * 幂等尝试开始：键不存在则原子写入 STARTING 并返回 true；已存在（重复 runId）返回 false。
     */
    public boolean tryStart(Long sessionId, String runId) {
        String key = key(sessionId, runId);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, STATE_STARTING, TTL);
        return Boolean.TRUE.equals(ok);
    }

    public void markFinished(Long sessionId, String runId) {
        set(sessionId, runId, STATE_FINISHED);
    }

    public void markFailed(Long sessionId, String runId) {
        set(sessionId, runId, STATE_FAILED);
    }

    private void set(Long sessionId, String runId, String state) {
        redisTemplate.opsForValue().set(key(sessionId, runId), state, TTL);
    }

    private String key(Long sessionId, String runId) {
        return properties.getRunKeyPrefix() + sessionId + ":" + runId;
    }
}
