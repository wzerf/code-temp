package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionMcpBindingProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Revision MCP 绑定实体（对齐 {@code agent_revision_mcp_binding}）。
 *
 * <p>关联表语义：无软删，解绑 = 物理删除。
 *
 * @author wshake
 */
@Data
@Table("agent_revision_mcp_binding")
@EntityProxy
public class AgentRevisionMcpBinding
        implements ProxyEntityAvailable<AgentRevisionMcpBinding, AgentRevisionMcpBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Revision。 */
    private Long agentRevisionId;

    /** 绑定的 Release。 */
    private Long mcpReleaseId;

    /** server 名。 */
    private String mcpName;

    /** Agent 层补配/覆盖的加密密钥密文。 */
    private String encryptedSecret;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;
}
