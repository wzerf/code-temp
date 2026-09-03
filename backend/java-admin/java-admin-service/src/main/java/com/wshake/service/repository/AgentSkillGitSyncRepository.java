package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillGitSync;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Git Skill 同步记录 Repository（唯一 (source_id, skill_path);保护人工修改）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillGitSyncRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillGitSync findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentSkillGitSync> listBySourceId(Long sourceId) {
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> t.sourceId().eq(sourceId))
                .orderBy(t -> t.skillPath().asc())
                .toList();
    }

    public AgentSkillGitSync findBySourceAndPath(Long sourceId, String skillPath) {
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> {
                    t.sourceId().eq(sourceId);
                    t.skillPath().eq(skillPath);
                })
                .firstOrNull();
    }

    /** 批量按草稿 id 反查同步记录(用于给草稿标注 Git 来源)。 */
    public List<AgentSkillGitSync> listByDraftIds(java.util.Collection<Long> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> t.draftId().in(draftIds))
                .toList();
    }

    public void insert(AgentSkillGitSync row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillGitSync row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
