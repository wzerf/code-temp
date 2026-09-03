package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Revision Skill Binding Repository（无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentRevisionSkillBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentRevisionSkillBinding findById(Long id) {
        return easyEntityQuery
                .queryable(AgentRevisionSkillBinding.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentRevisionSkillBinding> listByRevisionId(Long revisionId) {
        return easyEntityQuery
                .queryable(AgentRevisionSkillBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .orderBy(t -> t.skillName().asc())
                .toList();
    }

    public boolean existsName(Long revisionId, String skillName, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentRevisionSkillBinding.class)
                .where(t -> {
                    t.agentRevisionId().eq(revisionId);
                    t.skillName().eq(skillName);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentRevisionSkillBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long deleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentRevisionSkillBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.id().eq(id))
                .executeRows();
    }

    public long deleteByRevisionId(Long revisionId) {
        return easyEntityQuery
                .deletable(AgentRevisionSkillBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .executeRows();
    }

    public void insertAll(List<AgentRevisionSkillBinding> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(rows).executeRows(true);
    }
}
