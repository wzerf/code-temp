package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSessionProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** Agent 会话控制面元数据；运行状态仅由后续 Redis 运行面保存。 */
@Data
@Table("agent_session")
@EntityProxy
public class AgentSession implements ProxyEntityAvailable<AgentSession, AgentSessionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long agentDefinitionId;
    private Long agentRevisionId;
    private Long ownerUserId;
    private String status;
    private LocalDateTime createdAt;
}
