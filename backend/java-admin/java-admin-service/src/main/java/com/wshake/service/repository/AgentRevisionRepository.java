package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
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

    public List<AgentRevision> listByDefinitionId(Long definitionId) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> t.agentDefinitionId().eq(definitionId))
                .orderBy(t -> t.id().desc())
                .toList();
    }

    /** 某 Definition 下活跃 DRAFT（一个定义同一时刻应只有一条草稿）。 */
    public AgentRevision findActiveDraft(Long definitionId) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> {
                    t.agentDefinitionId().eq(definitionId);
                    t.status().eq("DRAFT");
                })
                .orderBy(t -> t.id().desc())
                .firstOrNull();
    }

    public EasyPageResult<AgentRevision> pageByDefinition(int page, int pageSize, Long definitionId, String status) {
        return easyEntityQuery
                .queryable(AgentRevision.class)
                .where(t -> {
                    t.agentDefinitionId().eq(definitionId);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public void insert(AgentRevision row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentRevision row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long updateStatus(Long id, String status) {
        return easyEntityQuery
                .updatable(AgentRevision.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentRevision.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
