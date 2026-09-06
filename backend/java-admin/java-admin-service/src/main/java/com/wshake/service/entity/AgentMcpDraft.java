package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentMcpDraftProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 连接配置草稿实体（对齐 {@code agent_mcp_draft}）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_mcp_draft")
@EntityProxy
public class AgentMcpDraft extends BaseEntity implements ProxyEntityAvailable<AgentMcpDraft, AgentMcpDraftProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** server 名（唯一键内）。 */
    private String name;

    /** MARKET / PRIVATE。 */
    private String visibility;

    /** DRAFT / PENDING_REVIEW / REJECTED / CONSUMED。 */
    private String status;

    /** sse / http（小写）。 */
    private String transport;

    /** 连接地址（HTTP/SSE endpoint）。 */
    private String url;

    /** 静态头（无密）。 */
    private String headersJson;

    /** 加密密钥密文（不存明文；MARKET 发布时剥离）。 */
    private String encryptedSecret;

    /** NONE=静态密钥直连；OAUTH=OAuth 登录。 */
    private String authType;

    /** OAuth Client ID（公开值；不支持 DCR 的服务必填）。 */
    private String oauthClientId;

    /** OAuth Client Secret 密文（MARKET 必须为空）。 */
    private String oauthClientSecretEnc;

    /** 空格分隔 scope（发布者预填；登录时可追加）。 */
    private String oauthScope;

    /** 发现缓存：授权端点。 */
    private String oauthAuthorizationEndpoint;

    /** 发现缓存：换票端点。 */
    private String oauthTokenEndpoint;

    /** MARKET 发布：approve 是否要求校验发布者登录态；0=可选跳过。 */
    private Integer oauthRequireLogin;

    /** 连接超时（毫秒）。 */
    private Integer connectTimeoutMs;

    /** 审核意见（对用户可见）。 */
    private String reviewComment;

    /** 审核人（0=未审）。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    private String remark;

    private Integer isEnabled;
}
