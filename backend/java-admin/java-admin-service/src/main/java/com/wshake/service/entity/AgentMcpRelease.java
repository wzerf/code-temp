package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentMcpReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Release 实体（对齐 {@code agent_mcp_release}）。
 *
 * <p>连接配置副本，工具目录不落库；MARKET 无密钥，PRIVATE 带密钥。
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

    /** 所有者。 */
    private Long ownerUserId;

    /** server 名。 */
    private String name;

    /** MARKET / PRIVATE。 */
    private String visibility;

    /** 在 (owner, visibility, name) 内递增。 */
    private Integer version;

    /** PUBLISHED / DEPRECATED。 */
    private String status;

    /** 来源草稿。 */
    private Long sourceDraftId;

    /** sse / http。 */
    private String transport;

    /** 连接地址。 */
    private String url;

    /** 静态头（无密，JSON 字符串）。 */
    private String headersJson;

    /** MARKET 无密钥，PRIVATE 带密钥。 */
    private String encryptedSecret;

    /** 连接超时（毫秒）。 */
    private Integer connectTimeoutMs;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
