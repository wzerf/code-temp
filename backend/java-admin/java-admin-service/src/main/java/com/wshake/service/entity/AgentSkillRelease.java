package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 不可变 Skill Release 快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_release")
@EntityProxy
public class AgentSkillRelease extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillRelease, AgentSkillReleaseProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String name;
    private Integer version;
    private String description;
    private String skillContent;
    private String visibility;
    private String status;
    private Long ownerUserId;
    private Long sourceDraftId;
    private String contentHash;
    private String source;
    private String remark;
    private Integer isEnabled;
}
