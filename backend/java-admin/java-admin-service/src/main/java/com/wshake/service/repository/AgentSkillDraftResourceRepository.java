package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillDraftResource;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill 草稿资源 Repository（子表,随草稿全量重写）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillDraftResourceRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<AgentSkillDraftResource> listByDraftId(Long draftId) {
        return easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().eq(draftId))
                .orderBy(t -> t.resourcePath().asc())
                .toList();
    }

    /** 批量统计各草稿的资源文件数。 */
    public Map<Long, Long> countByDraftIds(java.util.Collection<Long> draftIds) {
        Map<Long, Long> counts = new java.util.HashMap<>();
        if (draftIds == null || draftIds.isEmpty()) {
            return counts;
        }
        List<AgentSkillDraftResource> rows = easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().in(draftIds))
                .toList();
        for (AgentSkillDraftResource r : rows) {
            counts.merge(r.getDraftId(), 1L, Long::sum);
        }
        return counts;
    }

    public void insertAll(List<AgentSkillDraftResource> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }

    public long deleteByDraftId(Long draftId) {
        return easyEntityQuery
                .deletable(AgentSkillDraftResource.class)
                .allowDeleteStatement(true)
                .where(t -> t.draftId().eq(draftId))
                .executeRows();
    }
}
