package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.wshake.service.entity.AgentSkillInstall;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillInstallRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillInstall findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillInstall.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public AgentSkillInstall findActive(Long userId, String skillName, String visibility, Long ownerUserId) {
        return easyEntityQuery
                .queryable(AgentSkillInstall.class)
                .where(t -> {
                    t.userId().eq(userId);
                    t.skillName().eq(skillName);
                    t.visibility().eq(visibility);
                    t.ownerUserId().eq(ownerUserId);
                    t.deletedAt().eq(0L);
                })
                .firstOrNull();
    }

    public List<AgentSkillInstall> listByUserId(Long userId) {
        return easyEntityQuery
                .queryable(AgentSkillInstall.class)
                .where(t -> {
                    t.userId().eq(userId);
                    t.deletedAt().eq(0L);
                    t.isEnabled().eq(1);
                })
                .toList();
    }

    public void insert(AgentSkillInstall row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long update(AgentSkillInstall row) {
        return easyEntityQuery.updatable(row).executeRows();
    }
}
