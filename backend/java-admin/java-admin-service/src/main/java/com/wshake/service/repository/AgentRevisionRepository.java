package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevision;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Revision Repository。
 *
 * @author wshake
 */
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

    public List<AgentRevision> listByDefinition(Long definitionId) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> t.agentDefinitionId().eq(definitionId))
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public List<AgentRevision> listByDefinitionAndStatus(Long definitionId, String status) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> {
                    t.agentDefinitionId().eq(definitionId);
                    t.status().eq(status);
                })
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public List<AgentRevision> listPublishedByDefinition(Long definitionId) {
        return listByDefinitionAndStatus(definitionId, "PUBLISHED");
    }

    public void insert(AgentRevision row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentRevision row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentRevision.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
