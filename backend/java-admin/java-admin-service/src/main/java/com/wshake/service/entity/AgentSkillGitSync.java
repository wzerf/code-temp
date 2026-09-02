package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillGitSyncProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Git 包与最近一次导入草稿的映射，用于幂等同步和保护手工修改。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_git_sync")
@EntityProxy
public class AgentSkillGitSync extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillGitSync, AgentSkillGitSyncProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long sourceId;
    private String commitSha;
    private String skillPath;
    private String contentHash;
    private Long draftId;
    private Integer isEnabled;
}
