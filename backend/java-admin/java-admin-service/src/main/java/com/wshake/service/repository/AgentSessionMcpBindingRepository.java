package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSessionMcpBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Session MCP Binding Repository（无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSessionMcpBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSessionMcpBinding findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSessionMcpBinding.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentSessionMcpBinding> listBySessionId(Long sessionId) {
        return easyEntityQuery
                .queryable(AgentSessionMcpBinding.class)
                .where(t -> t.sessionId().eq(sessionId))
                .orderBy(t -> t.mcpName().asc())
                .toList();
    }

    public boolean existsName(Long sessionId, String mcpName, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentSessionMcpBinding.class)
                .where(t -> {
                    t.sessionId().eq(sessionId);
                    t.mcpName().eq(mcpName);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentSessionMcpBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long deleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSessionMcpBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
