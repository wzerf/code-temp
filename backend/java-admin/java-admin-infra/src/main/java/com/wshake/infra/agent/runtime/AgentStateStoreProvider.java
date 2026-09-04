package com.wshake.infra.agent.runtime;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * agentscope 会话状态存储提供者：平台 Redis 上的 {@link RedisAgentStateStore}。
 *
 * <p>对齐 docs/agent-module-architecture.md §2（控制状态与运行状态分离）：运行状态
 * （消息上下文/中断标记）存 Redis，控制面配置仍存 MySQL。key 前缀带平台模块名，
 * 避免与其他 Redis 业务（Sa-Token/nonce 等）冲突。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentStateStoreProvider {

    private final RedissonClient redissonClient;
    private final AgentRuntimeProperties properties;

    public AgentStateStore stateStore() {
        return RedisAgentStateStore.builder()
                .redissonClient(redissonClient)
                .keyPrefix(properties.getStateKeyPrefix())
                .build();
    }
}
