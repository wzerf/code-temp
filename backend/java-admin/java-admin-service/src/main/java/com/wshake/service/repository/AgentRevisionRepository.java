package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRevisionRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentRevision findById(Long id) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public void insert(AgentRevision row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentRevision row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
