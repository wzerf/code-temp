package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentSkillRelease;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill Release Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillReleaseRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillRelease findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public Integer maxVersion(Long ownerUserId, String visibility, String name) {
        AgentSkillRelease latest = easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                })
                .orderBy(t -> t.version().desc())
                .firstOrNull();
        return latest == null ? 0 : latest.getVersion();
    }

    public List<AgentSkillRelease> listMarket() {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.visibility().eq("MARKET");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public EasyPageResult<AgentSkillRelease> page(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(name != null, name);
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentSkillRelease> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> t.id().in(ids))
                .toList();
    }

    public void insert(AgentSkillRelease row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateStatus(Long id, String status) {
        return easyEntityQuery
                .updatable(AgentSkillRelease.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
