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
 * MCP 草稿实体（对齐 {@code agent_mcp_draft}）。
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

    /** server 名。 */
    private String name;

    /** sse / http。 */
    private String transport;

    /** 连接地址。 */
    private String url;

    /** 静态头（无密，JSON 字符串）。 */
    private String headersJson;

    /** 加密密钥密文（私有草稿可带）。 */
    private String encryptedSecret;

    /** 连接超时（毫秒）。 */
    private Integer connectTimeoutMs;

    /** MARKET / PRIVATE。 */
    private String visibility;

    /** DRAFT / PENDING_REVIEW / REJECTED / CONSUMED。 */
    private String status;

    /** 所有者。 */
    private Long ownerUserId;

    /** 审核意见。 */
    private String reviewComment;

    /** 审核人。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
