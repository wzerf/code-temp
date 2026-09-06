package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentMcpReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Release 实体（对齐 {@code agent_mcp_release},连接配置冻结副本,目录不入库）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_mcp_release")
@EntityProxy
public class AgentMcpRelease extends BaseEntity implements ProxyEntityAvailable<AgentMcpRelease, AgentMcpReleaseProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** server 名（冻结）。 */
    private String name;

    /** MARKET（无密钥）/ PRIVATE（带密钥）。 */
    private String visibility;

    /** PUBLISHED / DEPRECATED。 */
    private String status;

    /** 在 (owner,visibility,name) 内递增。 */
    private Integer version;

    /** sse / http（冻结）。 */
    private String transport;

    /** 连接地址（冻结）。 */
    private String url;

    /** 静态头（冻结）。 */
    private String headersJson;

    /** 加密密钥密文（MARKET Release 必须为空）。 */
    private String encryptedSecret;

    /** NONE=静态密钥直连；OAUTH=OAuth 登录（冻结）。 */
    private String authType;

    /** OAuth Client ID（冻结）。 */
    private String oauthClientId;

    /** OAuth Client Secret 密文（MARKET 必须为空；冻结）。 */
    private String oauthClientSecretEnc;

    /** 空格分隔 scope（冻结）。 */
    private String oauthScope;

    /** 授权端点（冻结）。 */
    private String oauthAuthorizationEndpoint;

    /** 换票端点（冻结）。 */
    private String oauthTokenEndpoint;

    /** MARKET 发布时的校验要求（冻结；运行时无用）。 */
    private Integer oauthRequireLogin;

    /** 连接超时（冻结）。 */
    private Integer connectTimeoutMs;

    /** 来源草稿 id（软引用）。 */
    private Long sourceDraftId;

    private String remark;

    private Integer isEnabled;
}
