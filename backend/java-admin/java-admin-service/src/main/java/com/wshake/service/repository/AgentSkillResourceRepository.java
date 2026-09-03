package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillReleaseResource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill 资源 Repository（草稿资源 + Release 资源）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillResourceRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<AgentSkillDraftResource> listDraftResources(Long draftId) {
        return easyEntityQuery
                .queryable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().eq(draftId))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public List<AgentSkillReleaseResource> listReleaseResources(Long releaseId) {
        return easyEntityQuery
                .queryable(AgentSkillReleaseResource.class)
                .where(t -> t.releaseId().eq(releaseId))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public void deleteDraftResources(Long draftId) {
        easyEntityQuery
                .deletable(AgentSkillDraftResource.class)
                .where(t -> t.draftId().eq(draftId))
                .executeRows();
    }

    public void insertDraftResource(AgentSkillDraftResource row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public void insertReleaseResource(AgentSkillReleaseResource row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }
}
