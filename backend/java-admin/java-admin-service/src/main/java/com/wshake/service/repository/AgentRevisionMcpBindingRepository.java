package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Revision MCP Binding Repository（无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentRevisionMcpBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentRevisionMcpBinding findById(Long id) {
        return easyEntityQuery
                .queryable(AgentRevisionMcpBinding.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentRevisionMcpBinding> listByRevisionId(Long revisionId) {
        return easyEntityQuery
                .queryable(AgentRevisionMcpBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .orderBy(t -> t.mcpName().asc())
                .toList();
    }

    public boolean existsName(Long revisionId, String mcpName, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentRevisionMcpBinding.class)
                .where(t -> {
                    t.agentRevisionId().eq(revisionId);
                    t.mcpName().eq(mcpName);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentRevisionMcpBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long deleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentRevisionMcpBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long deleteByRevisionId(Long revisionId) {
        return easyEntityQuery
                .deletable(AgentRevisionMcpBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .executeRows();
    }

    public void insertAll(List<AgentRevisionMcpBinding> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }
}
