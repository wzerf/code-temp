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

    public EasyPageResult<AgentSkillDraft> page(
            int page, int pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(nameLike != null, nameLike);
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentSkillDraft> listActiveByOwnerAndName(Long ownerUserId, String name, String visibility) {
        return easyEntityQuery
                .queryable(AgentSkillDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.visibility().eq(visibility);
                })
                .orderBy(t -> t.id().asc())
                .toList();
    }

    /** 是否存在活跃草稿（含 PENDING_REVIEW/REJECTED；CONSUMED 视为已消费不占位）。 */
    public boolean existsActiveDraft(Long ownerUserId, String name, String visibility, Long excludeId) {
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

    public void insert(AgentSkillDraft row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillDraft row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long updateStatus(
            Long id, String status, String reviewComment, Long reviewedBy, java.time.LocalDateTime reviewedAt) {
        return easyEntityQuery
                .updatable(AgentSkillDraft.class)
                .setColumns(t -> {
                    t.status().set(status);
                    t.reviewComment().set(reviewComment == null ? "" : reviewComment);
                    t.reviewedBy().set(reviewedBy == null ? 0L : reviewedBy);
                    if (reviewedAt != null) {
                        t.reviewedAt().set(reviewedAt);
                    }
                })
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSkillDraft.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
