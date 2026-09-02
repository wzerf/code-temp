package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillGitSync;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillGitSyncRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillGitSync findBySourceAndPath(Long sourceId, String skillPath) {
        return easyEntityQuery
                .queryable(AgentSkillGitSync.class)
                .where(t -> {
                    t.sourceId().eq(sourceId);
                    t.skillPath().eq(skillPath);
                    t.deletedAt().eq(0L);
                })
                .firstOrNull();
    }

    public void insert(AgentSkillGitSync sync) {
        easyEntityQuery.insertable(sync).executeRows(true);
    }

    public long update(AgentSkillGitSync sync) {
        return easyEntityQuery.updatable(sync).executeRows();
    }
}
