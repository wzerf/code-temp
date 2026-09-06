package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentMcpOauthStateProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * MCP OAuth 一次性登录态实体（对齐 {@code agent_mcp_oauth_state}，无软删，回调即删）。
 *
 * <p>暂存 state + PKCE verifier + 本次登录上下文；10 分钟过期。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_mcp_oauth_state")
public class AgentMcpOauthState implements ProxyEntityAvailable<AgentMcpOauthState, AgentMcpOauthStateProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 一次性 state（CSRF）。 */
    private String state;

    /** 目标 Release。 */
    private Long mcpReleaseId;

    /** 发起登录的用户（软引用 sys_user.id）。 */
    private Long userId;

    /** PKCE verifier（只存内存级随机串）。 */
    private String codeVerifier;

    /** 本次登录的回调地址。 */
    private String redirectUri;

    /** 本次请求的 scope。 */
    private String scope;

    /** RFC9728 resource 指示。 */
    private String resource;

    /** 本次使用的 client_id（DCR 或配置）。 */
    private String clientId;

    /** 过期时间（10 分钟）。 */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
