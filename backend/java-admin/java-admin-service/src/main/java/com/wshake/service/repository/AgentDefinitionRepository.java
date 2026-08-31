package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentDefinitionRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentDefinition findById(Long id) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public java.util.List<AgentDefinition> listByOwnerUserId(Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.deletedAt().eq(0L);
                })
                .toList();
    }

    public boolean existsByName(String name) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> t.name().eq(name))
                .any();
    }

    public void insert(AgentDefinition row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentDefinition row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
