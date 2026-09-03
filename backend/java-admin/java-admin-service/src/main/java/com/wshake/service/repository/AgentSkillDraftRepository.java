package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentSkillDraft;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill 草稿 Repository。
 *
 * @author wshake
 */
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

    public boolean existsActive(Long ownerUserId, String name, String visibility, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.visibility().eq(visibility);
                    t.status().ne("CONSUMED");
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public EasyPageResult<AgentSkillDraft> page(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(name != null, name);
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentSkillDraft> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> t.id().in(ids))
                .toList();
    }

    public void insert(AgentSkillDraft row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillDraft row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSkillDraft.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
