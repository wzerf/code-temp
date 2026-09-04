package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentModelDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型草稿 Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentModelDraftRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentModelDraft findById(Long id) {
        return easyEntityQuery
                .queryable(AgentModelDraft.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<AgentModelDraft> page(
            int page, int pageSize, Long ownerUserId, String nameLike, String scope, String status) {
        return easyEntityQuery
                .queryable(AgentModelDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(nameLike != null, nameLike);
                    t.scope().eq(scope != null, scope);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public boolean existsActiveDraft(Long ownerUserId, String name, String scope, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentModelDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.scope().eq(scope);
                    t.status().ne("CONSUMED");
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentModelDraft row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentModelDraft row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long updateStatus(
            Long id, String status, String reviewComment, Long reviewedBy, java.time.LocalDateTime reviewedAt) {
        return easyEntityQuery
                .updatable(AgentModelDraft.class)
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
                .deletable(AgentModelDraft.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
