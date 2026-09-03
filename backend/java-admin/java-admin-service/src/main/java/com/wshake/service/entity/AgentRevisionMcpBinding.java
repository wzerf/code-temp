package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionMcpBindingProxy;
import lombok.Data;

/**
 * Agent Revision MCP Binding（对齐 {@code agent_revision_mcp_binding},无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_revision_mcp_binding")
public class AgentRevisionMcpBinding
        implements ProxyEntityAvailable<AgentRevisionMcpBinding, AgentRevisionMcpBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Revision。 */
    private Long agentRevisionId;

    /** 绑定的 Release。 */
    private Long mcpReleaseId;

    /** server 名（从 Release 拷贝）。 */
    private String mcpName;

    /** Agent 层补配/覆盖的加密密钥密文（市场 MCP 在此配密钥）。 */
    private String encryptedSecret;

    /** 创建人（0=系统操作）。 */
    private Long createdBy;
}
