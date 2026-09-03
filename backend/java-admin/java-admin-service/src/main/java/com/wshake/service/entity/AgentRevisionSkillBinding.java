package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionSkillBindingProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Revision Skill 绑定实体（对齐 {@code agent_revision_skill_binding}）。
 *
 * <p>关联表语义：无软删，解绑 = 物理删除。
 *
 * @author wshake
 */
@Data
@Table("agent_revision_skill_binding")
@EntityProxy
public class AgentRevisionSkillBinding
        implements ProxyEntityAvailable<AgentRevisionSkillBinding, AgentRevisionSkillBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Revision。 */
    private Long agentRevisionId;

    /** 绑定的 Release 快照。 */
    private Long skillReleaseId;

    /** 从 Release 拷贝。 */
    private String skillName;

    /** 运行漂移校验用。 */
    private String contentHash;

    /** 同名冲突时的胜者标记。 */
    private Integer overrideWinner;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;
}
