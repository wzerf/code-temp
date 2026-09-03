package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSessionProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 会话实体（对齐 {@code agent_session},控制面元数据）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_session")
@EntityProxy
public class AgentSession extends BaseEntity implements ProxyEntityAvailable<AgentSession, AgentSessionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Definition。 */
    private Long agentDefinitionId;

    /** 固定 Revision（首启前 NULL）。 */
    private Long agentRevisionId;

    /** 会话所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** ACTIVE=活跃。 */
    private String status;

    /** 最近活跃时间。 */
    private LocalDateTime lastActiveAt;

    private String remark;

    private Integer isEnabled;
}
