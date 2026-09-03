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

    public EasyPageResult<AgentMcpRelease> page(
            int page, int pageSize, String visibility, String status, String nameLike) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                    t.name().like(nameLike != null, nameLike);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    /** 市场列表 = visibility=MARKET 且 status=PUBLISHED。 */
    public List<AgentMcpRelease> listMarket() {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.visibility().eq("MARKET");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .toList();
    }

    public List<AgentMcpRelease> listByNameAllVersions(Long ownerUserId, String visibility, String name) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                })
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public AgentMcpRelease findLatestActive(Long ownerUserId, String visibility, String name) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.version().desc())
                .firstOrNull();
    }

    /** 供绑定候选:某 owner 的全部 PRIVATE PUBLISHED Release（按 name 聚合由 service 完成）。 */
    public List<AgentMcpRelease> listByOwnerForBind(Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentMcpRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq("PRIVATE");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
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
