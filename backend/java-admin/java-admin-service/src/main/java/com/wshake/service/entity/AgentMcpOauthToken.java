package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentMcpOauthTokenProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * MCP OAuth 用户 token 实体（对齐 {@code agent_mcp_oauth_token}，无软删，撤销=物理删）。
 *
 * <p>按 (release, user) 隔离；access/refresh 密文落库，明文只在内存。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_mcp_oauth_token")
public class AgentMcpOauthToken implements ProxyEntityAvailable<AgentMcpOauthToken, AgentMcpOauthTokenProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 绑定的 Release。 */
    private Long mcpReleaseId;

    /** 登录用户（软引用 sys_user.id）。 */
    private Long userId;

    /** access_token 密文（不存明文）。 */
    private String accessTokenEnc;

    /** refresh_token 密文（可空）。 */
    private String refreshTokenEnc;

    /** token 类型。 */
    private String tokenType;

    /** 过期时间（NULL=服务端未给）。 */
    private LocalDateTime expiresAt;

    /** 实际授予 scope。 */
    private String scope;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
