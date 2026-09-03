package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillGitSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Git Skill 来源 Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillGitSourceRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillGitSource findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    /** 批量取来源。 */
    public List<AgentSkillGitSource> listByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> t.id().in(ids))
                .toList();
    }

    public List<AgentSkillGitSource> listByScope(String scope, Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> {
                    t.scope().eq(scope);
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                })
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public void insert(AgentSkillGitSource row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillGitSource row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSkillGitSource.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
