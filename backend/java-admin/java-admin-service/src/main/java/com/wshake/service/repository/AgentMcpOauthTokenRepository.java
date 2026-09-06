package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentMcpOauthToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP OAuth 用户 token Repository（按 release+user 隔离）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentMcpOauthTokenRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentMcpOauthToken findByReleaseAndUser(Long releaseId, Long userId) {
        return easyEntityQuery
                .queryable(AgentMcpOauthToken.class)
                .where(t -> {
                    t.mcpReleaseId().eq(releaseId);
                    t.userId().eq(userId);
                })
                .firstOrNull();
    }

    /** upsert：存在则更新，不存在则插入。 */
    public void upsert(AgentMcpOauthToken row) {
        AgentMcpOauthToken existing = findByReleaseAndUser(row.getMcpReleaseId(), row.getUserId());
        if (existing == null) {
            easyEntityQuery.insertable(row).executeRows(true);
            return;
        }
        easyEntityQuery
                .updatable(AgentMcpOauthToken.class)
                .setColumns(t -> {
                    t.accessTokenEnc().set(row.getAccessTokenEnc());
                    t.refreshTokenEnc().set(row.getRefreshTokenEnc());
                    t.tokenType().set(row.getTokenType());
                    t.expiresAt().set(row.getExpiresAt());
                    t.scope().set(row.getScope());
                })
                .where(t -> t.id().eq(existing.getId()))
                .executeRows();
    }

    public void deleteByReleaseAndUser(Long releaseId, Long userId) {
        easyEntityQuery
                .deletable(AgentMcpOauthToken.class)
                .where(t -> {
                    t.mcpReleaseId().eq(releaseId);
                    t.userId().eq(userId);
                })
                .executeRows();
    }
}
