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
 * Agent 会话实体（对齐 {@code agent_session}）。
 *
 * <p>仅控制面元数据；运行状态在 Redis。
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

    /** 会话所有者。 */
    private Long ownerUserId;

    /** ACTIVE。 */
    private String status;

    /** 最近活跃时间。 */
    private LocalDateTime lastActiveAt;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
