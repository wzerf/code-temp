package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSessionModelBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Session 模型选择 Repository（无软删，解绑=物理删）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSessionModelBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSessionModelBinding findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSessionModelBinding.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public AgentSessionModelBinding findBySessionId(Long sessionId) {
        return easyEntityQuery
                .queryable(AgentSessionModelBinding.class)
                .where(t -> t.sessionId().eq(sessionId))
                .firstOrNull();
    }

    public void insert(AgentSessionModelBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSessionModelBinding row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long deleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSessionModelBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long deleteBySessionId(Long sessionId) {
        return easyEntityQuery
                .deletable(AgentSessionModelBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.sessionId().eq(sessionId))
                .executeRows();
    }
}
