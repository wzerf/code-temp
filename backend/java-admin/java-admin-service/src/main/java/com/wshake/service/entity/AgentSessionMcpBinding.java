package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSessionMcpBindingProxy;
import lombok.Data;

/**
 * Agent Session MCP Binding（对齐 {@code agent_session_mcp_binding},无软删,解绑=物理删）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_session_mcp_binding")
public class AgentSessionMcpBinding
        implements ProxyEntityAvailable<AgentSessionMcpBinding, AgentSessionMcpBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属会话。 */
    private Long sessionId;

    /** 绑定的 Release。 */
    private Long mcpReleaseId;

    /** server 名（Session 内唯一；同名覆盖 Revision）。 */
    private String mcpName;

    /** Session 绑定时补配/覆盖的加密密钥密文。 */
    private String encryptedSecret;

    /** 创建人（0=系统操作）。 */
    private Long createdBy;
}
