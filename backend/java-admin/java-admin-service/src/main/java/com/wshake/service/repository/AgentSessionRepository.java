package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 会话 Repository（控制面元数据）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSessionRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSession findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSession.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<AgentSession> pageByDefinition(int page, int pageSize, Long definitionId, Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSession.class)
                .where(t -> {
                    t.agentDefinitionId().eq(definitionId);
                    t.ownerUserId().eq(ownerUserId != null, ownerUserId);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    public List<AgentSession> listByDefinition(Long definitionId) {
        return easyEntityQuery
                .queryable(AgentSession.class)
                .where(t -> t.agentDefinitionId().eq(definitionId))
                .orderBy(t -> t.id().desc())
                .toList();
    }

    public void insert(AgentSession row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateRevisionId(Long id, Long revisionId) {
        return easyEntityQuery
                .updatable(AgentSession.class)
                .setColumns(t -> t.agentRevisionId().set(revisionId))
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    /** 记住或清除会话模型选择；{@code modelReleaseId == null} 写 NULL。 */
    public long updateModelReleaseId(Long id, Long modelReleaseId) {
        return easyEntityQuery
                .updatable(AgentSession.class)
                .setColumns(t -> {
                    if (modelReleaseId == null) {
                        t.modelReleaseId().setNull();
                    } else {
                        t.modelReleaseId().set(modelReleaseId);
                    }
                })
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long softDeleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSession.class)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
