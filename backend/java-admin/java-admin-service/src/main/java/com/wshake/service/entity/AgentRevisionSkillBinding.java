package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionSkillBindingProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent Revision 绑定的 Skill Release 快照指针。 */
@Data
@Table("agent_revision_skill_binding")
@EntityProxy
public class AgentRevisionSkillBinding
        implements ProxyEntityAvailable<AgentRevisionSkillBinding, AgentRevisionSkillBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long agentRevisionId;
    private Long skillReleaseId;
    private String skillName;
    private String contentHash;
    private Integer overrideWinner;
    private LocalDateTime createdAt;
}
