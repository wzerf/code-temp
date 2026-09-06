package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentMcpOauthState;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP OAuth 一次性登录态 Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentMcpOauthStateRepository {

    private final EasyEntityQuery easyEntityQuery;

    public void insert(AgentMcpOauthState row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    /** 取出一次性 state（不存在/过期返回 null；调用方负责删除）。 */
    public AgentMcpOauthState findValidByState(String state, LocalDateTime now) {
        return easyEntityQuery
                .queryable(AgentMcpOauthState.class)
                .where(t -> {
                    t.state().eq(state);
                    t.expiresAt().gt(now);
                })
                .firstOrNull();
    }

    public void deleteById(Long id) {
        easyEntityQuery
                .deletable(AgentMcpOauthState.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /** 顺手清理过期 state（回调/登录时调用，不阻塞主流程）。 */
    public void deleteExpired(LocalDateTime now) {
        easyEntityQuery
                .deletable(AgentMcpOauthState.class)
                .where(t -> t.expiresAt().le(now))
                .executeRows();
    }
}
