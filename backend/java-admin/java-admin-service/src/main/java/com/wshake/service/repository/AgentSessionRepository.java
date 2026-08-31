package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSessionRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSession findByIdAndOwnerUserId(Long id, Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSession.class)
                .where(t -> {
                    t.id().eq(id);
                    t.ownerUserId().eq(ownerUserId);
                })
                .firstOrNull();
    }

    public java.util.List<AgentSession> listByOwnerUserIdAndAgentDefinitionId(
            Long ownerUserId, Long agentDefinitionId) {
        return easyEntityQuery
                .queryable(AgentSession.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.agentDefinitionId().eq(agentDefinitionId);
                })
                .orderBy(t -> {
                    t.lastActiveAt().desc();
                    t.id().desc();
                })
                .toList();
    }

    public long bindRevisionIfUnbound(Long id, Long ownerUserId, Long revisionId) {
        return easyEntityQuery
                .updatable(AgentSession.class)
                .setColumns(t -> t.agentRevisionId().set(revisionId))
                .where(t -> {
                    t.id().eq(id);
                    t.ownerUserId().eq(ownerUserId);
                    t.agentRevisionId().isNull();
                })
                .executeRows();
    }

    public long touch(Long id, Long ownerUserId, java.time.LocalDateTime lastActiveAt) {
        return easyEntityQuery
                .updatable(AgentSession.class)
                .setColumns(t -> t.lastActiveAt().set(lastActiveAt))
                .where(t -> {
                    t.id().eq(id);
                    t.ownerUserId().eq(ownerUserId);
                })
                .executeRows();
    }

    public void insert(AgentSession row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSession row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
