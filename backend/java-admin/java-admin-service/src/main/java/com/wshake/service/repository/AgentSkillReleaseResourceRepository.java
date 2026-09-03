package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillReleaseResource;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill Release 冻结资源 Repository（不可变,只插不更）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillReleaseResourceRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<AgentSkillReleaseResource> listByReleaseId(Long releaseId) {
        return easyEntityQuery
                .queryable(AgentSkillReleaseResource.class)
                .where(t -> t.releaseId().eq(releaseId))
                .orderBy(t -> t.resourcePath().asc())
                .toList();
    }

    /** 批量统计各 Release 的资源文件数。 */
    public Map<Long, Long> countByReleaseIds(java.util.Collection<Long> releaseIds) {
        Map<Long, Long> counts = new java.util.HashMap<>();
        if (releaseIds == null || releaseIds.isEmpty()) {
            return counts;
        }
        List<AgentSkillReleaseResource> rows = easyEntityQuery
                .queryable(AgentSkillReleaseResource.class)
                .where(t -> t.releaseId().in(releaseIds))
                .toList();
        for (AgentSkillReleaseResource r : rows) {
            counts.merge(r.getReleaseId(), 1L, Long::sum);
        }
        return counts;
    }

    public void insertAll(List<AgentSkillReleaseResource> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }
}
