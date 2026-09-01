package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRevisionSkillBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<AgentRevisionSkillBinding> listByRevisionId(Long revisionId) {
        return easyEntityQuery
                .queryable(AgentRevisionSkillBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .toList();
    }

    public void replace(Long revisionId, List<AgentRevisionSkillBinding> rows) {
        easyEntityQuery
                .deletable(AgentRevisionSkillBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .allowDeleteStatement(true)
                .executeRows();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }

    public void insertAll(List<AgentRevisionSkillBinding> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }
}
