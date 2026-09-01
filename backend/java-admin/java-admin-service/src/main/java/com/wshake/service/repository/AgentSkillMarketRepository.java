package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillMarket;
import com.wshake.service.entity.AgentSkillMarketResource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillMarketRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillMarket findByName(String name) {
        return easyEntityQuery
                .queryable(AgentSkillMarket.class)
                .where(t -> t.name().eq(name))
                .firstOrNull();
    }

    public List<AgentSkillMarket> listAll() {
        return easyEntityQuery
                .queryable(AgentSkillMarket.class)
                .orderBy(t -> t.name().asc())
                .toList();
    }

    public void insert(AgentSkillMarket row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillMarket row) {
        return easyEntityQuery.updatable(row).executeRows();
    }

    public void deleteByName(String name) {
        AgentSkillMarket row = findByName(name);
        if (row == null) {
            return;
        }
        easyEntityQuery
                .deletable(AgentSkillMarketResource.class)
                .where(t -> t.id().eq(row.getId()))
                .allowDeleteStatement(true)
                .executeRows();
        easyEntityQuery
                .deletable(AgentSkillMarket.class)
                .where(t -> t.id().eq(row.getId()))
                .allowDeleteStatement(true)
                .executeRows();
    }

    public List<AgentSkillMarketResource> listResources(Long skillId) {
        return easyEntityQuery
                .queryable(AgentSkillMarketResource.class)
                .where(t -> t.id().eq(skillId))
                .toList();
    }

    public void replaceResources(Long skillId, List<AgentSkillMarketResource> resources) {
        easyEntityQuery
                .deletable(AgentSkillMarketResource.class)
                .where(t -> t.id().eq(skillId))
                .allowDeleteStatement(true)
                .executeRows();
        if (resources == null || resources.isEmpty()) {
            return;
        }
        easyEntityQuery.insertable(resources).executeRows();
    }
}
