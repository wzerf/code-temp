package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentMcpRelease;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP Release Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentMcpReleaseRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentMcpRelease findById(Long id) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public Integer maxVersion(Long ownerUserId, String visibility, String name) {
        AgentMcpRelease latest = easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                })
                .orderBy(t -> t.version().desc())
                .firstOrNull();
        return latest == null ? 0 : latest.getVersion();
    }

    public List<AgentMcpRelease> listMarket() {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.visibility().eq("MARKET");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public EasyPageResult<AgentMcpRelease> page(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(name != null, name);
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentMcpRelease> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> t.id().in(ids))
                .toList();
    }

    public void insert(AgentMcpRelease row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateStatus(Long id, String status) {
        return easyEntityQuery
                .updatable(AgentMcpRelease.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
