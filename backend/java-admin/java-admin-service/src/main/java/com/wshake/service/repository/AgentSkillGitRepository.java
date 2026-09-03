package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.entity.AgentSkillGitSync;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Git Skill 来源 / 同步记录 Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillGitRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillGitSource findSourceById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentSkillGitSource> listSources() {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public boolean existsSource(String scope, Long ownerUserId, String url, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> {
                    t.scope().eq(scope);
                    t.ownerUserId().eq(ownerUserId);
                    t.url().eq(url);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public AgentSkillGitSync findSync(Long sourceId, String skillPath) {
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> {
                    t.sourceId().eq(sourceId);
                    t.skillPath().eq(skillPath);
                })
                .firstOrNull();
    }

    public void insertSource(AgentSkillGitSource row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateSource(AgentSkillGitSource row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteSource(Long id) {
        return easyEntityQuery
                .deletable(AgentSkillGitSource.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public void insertSync(AgentSkillGitSync row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateSync(AgentSkillGitSync row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
