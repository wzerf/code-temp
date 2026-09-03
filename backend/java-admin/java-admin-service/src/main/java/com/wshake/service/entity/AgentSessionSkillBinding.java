package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSessionSkillBindingProxy;
import lombok.Data;

/**
 * Agent Session Skill Binding（对齐 {@code agent_session_skill_binding},无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_session_skill_binding")
public class AgentSessionSkillBinding
        implements ProxyEntityAvailable<AgentSessionSkillBinding, AgentSessionSkillBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属会话。 */
    private Long sessionId;

    /** 绑定的 Release 快照。 */
    private Long skillReleaseId;

    /** skill_name（Session 内唯一；同名覆盖 Revision）。 */
    private String skillName;

    /** 从 Release 拷贝（运行漂移校验用）。 */
    private String contentHash;

    /** 创建人（0=系统操作）。 */
    private Long createdBy;
}
