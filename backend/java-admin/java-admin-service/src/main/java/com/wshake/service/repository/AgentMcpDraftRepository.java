package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentMcpDraft;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP 草稿 Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentMcpDraftRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentMcpDraft findById(Long id) {
        return easyEntityQuery
                .queryable(AgentMcpDraft.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<AgentMcpDraft> page(
            int page, int pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
        return easyEntityQuery
                .queryable(AgentMcpDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                    t.name().like(nameLike != null, nameLike);
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentMcpDraft> listActiveByOwnerAndName(Long ownerUserId, String name, String visibility) {
        return easyEntityQuery
                .queryable(AgentMcpDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.visibility().eq(visibility);
                })
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public boolean existsActiveDraft(Long ownerUserId, String name, String visibility, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentMcpDraft.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.name().eq(name);
                    t.visibility().eq(visibility);
                    t.status().ne("CONSUMED");
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentMcpDraft row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentMcpDraft row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public long updateStatus(
            Long id, String status, String reviewComment, Long reviewedBy, java.time.LocalDateTime reviewedAt) {
        return easyEntityQuery
                .updatable(AgentMcpDraft.class)
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
                .deletable(AgentMcpDraft.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
