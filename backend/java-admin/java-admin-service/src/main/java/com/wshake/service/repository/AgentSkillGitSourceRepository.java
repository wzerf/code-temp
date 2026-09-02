package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillGitSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillGitSourceRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillGitSource findById(Long id) {
        return easyEntityQuery.queryable(AgentSkillGitSource.class).where(t -> t.id().eq(id)).firstOrNull();
    }

    public List<AgentSkillGitSource> listPrivateByOwner(Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> {
                    t.deletedAt().eq(0L);
                    t.scope().eq("PRIVATE");
                    t.ownerUserId().eq(ownerUserId);
                })
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public List<AgentSkillGitSource> listMarket() {
        return easyEntityQuery
                .queryable(AgentSkillGitSource.class)
                .where(t -> {
                    t.deletedAt().eq(0L);
                    t.scope().eq("MARKET");
                })
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public void insert(AgentSkillGitSource source) {
        easyEntityQuery.insertable(source).executeRows(true);
    }

    public long update(AgentSkillGitSource source) {
        return easyEntityQuery.updatable(source).executeRows();
    }

    public void delete(AgentSkillGitSource source) {
        easyEntityQuery.deletable(source).executeRows();
    }
}
