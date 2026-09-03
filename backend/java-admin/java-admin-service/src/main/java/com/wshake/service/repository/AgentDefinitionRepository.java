package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Definition Repository。
 *
 * @author wshake
 */
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

    public boolean existsByName(String name, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> {
                    t.name().eq(name);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public EasyPageResult<AgentDefinition> page(int page, int pageSize, String name, Integer status) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> {
                    t.name().like(name != null, name);
                    t.isEnabled().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentDefinition> listAll() {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public void insert(AgentDefinition row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentDefinition row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentDefinition.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateIsEnabled(Long id, int isEnabled) {
        return easyEntityQuery
                .updatable(AgentDefinition.class)
                .setColumns(t -> t.isEnabled().set(isEnabled))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long updateCurrentPublishedRevision(Long id, Long revisionId) {
        return easyEntityQuery
                .updatable(AgentDefinition.class)
                .setColumns(t -> t.currentPublishedRevisionId().set(revisionId))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
