package com.wshake.service.repository;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.service.entity.AgentSkillRelease;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Skill Release Repository。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentSkillReleaseRepository {

    private final EasyEntityQuery easyEntityQuery;

    public AgentSkillRelease findById(Long id) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> t.id().eq(id))
                .firstOrNull();
    }

    public EasyPageResult<AgentSkillRelease> page(
            int page, int pageSize, String visibility, String status, String nameLike) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.visibility().eq(visibility != null, visibility);
                    t.status().eq(status != null, status);
                    t.name().like(nameLike != null, nameLike);
                })
                .orderBy(t -> t.id().desc())
                .toPageResult(page, pageSize);
    }

    /** 市场列表 = visibility=MARKET 且 status=PUBLISHED,按 name 取 version 最大一条。 */
    public List<AgentSkillRelease> listMarket() {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.visibility().eq("MARKET");
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.name().asc())
                .toList();
    }

    public List<AgentSkillRelease> listByNameAllVersions(Long ownerUserId, String visibility, String name) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                })
                .orderBy(t -> t.version().desc())
                .toList();
    }

    /** 当前可绑定(未弃用)的最近版本 Release。 */
    public AgentSkillRelease findLatestActive(Long ownerUserId, String visibility, String name) {
        return easyEntityQuery
                .queryable(AgentSkillRelease.class)
                .where(t -> {
                    t.ownerUserId().eq(ownerUserId);
                    t.visibility().eq(visibility);
                    t.name().eq(name);
                    t.status().eq("PUBLISHED");
                })
                .orderBy(t -> t.version().desc())
                .firstOrNull();
    }

    /** 供前端「可绑定 Release 下拉」使用的候选:最近 PUBLISHED 版本;仅看 MARKET 或本人 PRIVATE。 */
    public AgentSkillRelease findLatestBindable(Long ownerUserId, String visibility, String name) {
        return findLatestActive(ownerUserId, visibility, name);
    }

    public void insert(AgentSkillRelease row) {
        easyEntityQuery.insertable(row).executeRows(true);
    }

    public long updateStatus(Long id, String status) {
        return easyEntityQuery
                .updatable(AgentSkillRelease.class)
                .setColumns(t -> t.status().set(status))
                .where(t -> t.id().eq(id))
                .executeRows();
    }
}
