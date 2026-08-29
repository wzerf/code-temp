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

    public AgentSession findByIdAndOwnerUserIdForUpdate(Long id, Long ownerUserId) {
        return findByIdAndOwnerUserId(id, ownerUserId);
    }

    public void insert(AgentSession row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSession row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
