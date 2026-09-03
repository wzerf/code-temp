package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionSkillBindingProxy;
import lombok.Data;

/**
 * Agent Revision Skill Binding（对齐 {@code agent_revision_skill_binding},无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_revision_skill_binding")
public class AgentRevisionSkillBinding
        implements ProxyEntityAvailable<AgentRevisionSkillBinding, AgentRevisionSkillBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Revision。 */
    private Long agentRevisionId;

    /** 绑定的 Release 快照。 */
    private Long skillReleaseId;

    /** 从 Release 拷贝的 skill_name。 */
    private String skillName;

    /** 从 Release 拷贝（运行漂移校验用）。 */
    private String contentHash;

    /** 同名冲突（市场vs私有）胜者标记；Revision 内同 skill_name 恰好一条=1。 */
    private Integer overrideWinner;

    /** 创建人（0=系统操作）。 */
    private Long createdBy;
}
