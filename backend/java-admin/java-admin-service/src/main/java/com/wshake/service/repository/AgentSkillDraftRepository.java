package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillDraftRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillDraft findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public AgentSkillDraft findActiveByOwnerAndName(Long ownerUserId, String name) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.deletedAt().eq(0L);
                })
                .firstOrNull();
    }

    public List<AgentSkillDraft> listByOwnerUserId(Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.deletedAt().eq(0L);
                })
                .toList();
    }

    public void insert(AgentSkillDraft row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillDraft row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public List<AgentSkillDraftResource> listResources(Long draftId) {
        return easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().eq(draftId))
                .toList();
    }

    public AgentSkillDraftResource findResourceById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public AgentSkillDraftResource findResourceByDraftIdAndPath(Long draftId, String path) {
        return easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> {
                    t.draftId().eq(draftId);
                    t.resourcePath().eq(path);
                })
                .firstOrNull();
    }

    public void insertResource(AgentSkillDraftResource row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateResource(AgentSkillDraftResource row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public void deleteResource(Long id) {
        easyEntityQuery
                .deletable(AgentSkillDraftResource.class)
                .where(t -> t.id().eq(id))
                .allowDeleteStatement(true)
                .executeRows();
    }

    public void replaceResources(Long draftId, List<AgentSkillDraftResource> resources) {
        easyEntityQuery
                .deletable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().eq(draftId))
                .allowDeleteStatement(true)
                .executeRows();
        if (resources == null || resources.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(resources).executeRows();
    }
}
