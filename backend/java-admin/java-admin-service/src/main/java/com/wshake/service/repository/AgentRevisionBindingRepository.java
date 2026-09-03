package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Revision 绑定 Repository（Skill / MCP）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentRevisionBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public List<AgentRevisionSkillBinding> listSkillBindings(Long revisionId) {
        return easyEntityQuery
                .queryable(AgentRevisionSkillBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public List<AgentRevisionMcpBinding> listMcpBindings(Long revisionId) {
        return easyEntityQuery
                .queryable(AgentRevisionMcpBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .orderBy(t -> t.id().asc())
                .toList();
    }

    public void deleteSkillBindings(Long revisionId) {
        easyEntityQuery
                .deletable(AgentRevisionSkillBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .executeRows();
    }

    public void deleteMcpBindings(Long revisionId) {
        easyEntityQuery
                .deletable(AgentRevisionMcpBinding.class)
                .where(t -> t.agentRevisionId().eq(revisionId))
                .executeRows();
    }

    public void insertSkillBinding(AgentRevisionSkillBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public void insertMcpBinding(AgentRevisionMcpBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }
}
