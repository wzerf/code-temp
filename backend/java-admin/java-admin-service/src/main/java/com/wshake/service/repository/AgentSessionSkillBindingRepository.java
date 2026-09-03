package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSessionSkillBinding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Session Skill Binding Repository（无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSessionSkillBindingRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSessionSkillBinding findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSessionSkillBinding.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public List<AgentSessionSkillBinding> listBySessionId(Long sessionId) {
        return easyEntityQuery
                .queryable(AgentSessionSkillBinding.class)
                .where(t -> t.sessionId().eq(sessionId))
                .orderBy(t -> t.skillName().asc())
                .toList();
    }

    public boolean existsName(Long sessionId, String skillName, Long excludeId) {
        return easyEntityQuery
                .queryable(AgentSessionSkillBinding.class)
                .where(t -> {
                    t.sessionId().eq(sessionId);
                    t.skillName().eq(skillName);
                    t.id().ne(excludeId != null, excludeId);
                })
                .any();
    }

    public void insert(AgentSessionSkillBinding row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long deleteById(Long id) {
        return easyEntityQuery
                .deletable(AgentSessionSkillBinding.class)
                .allowDeleteStatement(true)
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
