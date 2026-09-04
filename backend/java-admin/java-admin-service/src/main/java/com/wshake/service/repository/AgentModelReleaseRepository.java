package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentModelRelease;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型 Release Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentModelReleaseRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentModelRelease findById(Long id) {
        return easyEntityQuery
                .queryable(AgentModelRelease.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<AgentModelRelease> page(
            int page, int pageSize, String scope, String status, String nameLike) {
        return easyEntityQuery
                .queryable(AgentModelRelease.class)
                .where(t -> {
                    t.scope().eq(scope != null, scope);
                    t.status().eq(status != null, status);
                    t.name().like(nameLike != null, nameLike);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    /** 官方可用池 = scope=OFFICIAL 且 status=PUBLISHED。 */
    public List<AgentModelRelease> listOfficialPublished() {
        return easyEntityQuery
                .queryable(AgentModelRelease.class)
                .where(t -> {
                    t.scope().eq("OFFICIAL");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .toList();
    }

    /** 调用者私有可用池。 */
    public List<AgentModelRelease> listPrivatePublishedByOwner(Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentModelRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.scope().eq("PRIVATE");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .toList();
    }

    public List<AgentModelRelease> listByNameAllVersions(Long ownerUserId, String scope, String name) {
        return easyEntityQuery
                .queryable(AgentModelRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.scope().eq(scope);
                    t.name().eq(name);
                })
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public void insert(AgentModelRelease row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateStatus(Long id, String status) {
        return easyEntityQuery
                .updatable(AgentModelRelease.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
