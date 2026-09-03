package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 定义 Repository。
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

    public AgentDefinition findByName(String name) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> t.name().eq(name))
                .firstOrNull();
    }

    public EasyPageResult<AgentDefinition> page(int page, int pageSize, String nameLike, Integer isEnabled) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> {
                    t.name().like(nameLike != null, nameLike);
                    t.isEnabled().eq(isEnabled != null, isEnabled);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentDefinition> listAll(Integer isEnabled) {
        return easyEntityQuery
                .queryable(AgentDefinition.class)
                .where(t -> t.isEnabled().eq(isEnabled != null, isEnabled))
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public void insert(AgentDefinition row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentDefinition row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long updateCurrentPublishedRevisionId(Long id, Long revisionId) {
        return easyEntityQuery
                .updatable(AgentDefinition.class)
                .setColumns(t -> t.currentPublishedRevisionId().set(revisionId))
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

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentDefinition.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
