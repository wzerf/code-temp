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
