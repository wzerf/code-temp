package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillInstallProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户对 Skill 的安装资格。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_install")
@EntityProxy
public class AgentSkillInstall extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillInstall, AgentSkillInstallProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long userId;
    private String skillName;
    private String visibility;
    private Long ownerUserId;
    private String remark;
    private Integer isEnabled;
}
