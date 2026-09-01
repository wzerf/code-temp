package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.entity.AgentSkillReleaseResource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public AgentSkillRelease findLatest(Long ownerUserId, String visibility, String name) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                    t.deletedAt().eq(0L);
                })
                .orderBy(t -> t.version().desc())
                .firstOrNull();
    }

    public List<AgentSkillRelease> listPublished(String visibility, Long ownerUserId, String name) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.visibility().eq(visibility);
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.status().eq("PUBLISHED");
                    t.deletedAt().eq(0L);
                    t.isEnabled().eq(1);
                })
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public List<AgentSkillRelease> listPublishedByOwner(Long ownerUserId, String visibility) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.status().eq("PUBLISHED");
                    t.deletedAt().eq(0L);
                    t.isEnabled().eq(1);
                })
                .orderBy(t -> t.name().asc())
                .orderBy(t -> t.version().desc())
                .toList();
    }

    public void insert(AgentSkillRelease row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillRelease row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public List<AgentSkillReleaseResource> listResources(Long releaseId) {
        return easyEntityQuery
                .queryable(AgentSkillReleaseResource.class)
                .where(t -> t.releaseId().eq(releaseId))
                .toList();
    }

    public void insertResources(List<AgentSkillReleaseResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(resources).executeRows();
    }
}
