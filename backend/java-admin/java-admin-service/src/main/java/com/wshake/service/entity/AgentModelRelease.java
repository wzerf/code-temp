package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentModelReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型 Release 实体（对齐 {@code agent_model_release}，连接配置冻结副本）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_model_release")
@EntityProxy
public class AgentModelRelease extends BaseEntity
        implements ProxyEntityAvailable<AgentModelRelease, AgentModelReleaseProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** 模型显示名（冻结）。 */
    private String name;

    /** OFFICIAL / PRIVATE（冻结）。 */
    private String scope;

    /** 功能码（冻结）。 */
    private String code;

    /** PUBLISHED / DEPRECATED。 */
    private String status;

    /** 在 (owner,scope,name) 内递增。 */
    private Integer version;

    /** openai-compatible / anthropic（冻结）。 */
    private String provider;

    /** 连接地址（冻结）。 */
    private String baseUrl;

    /** 远端模型标识（冻结）。 */
    private String modelName;

    /** 能力 JSON（冻结）。 */
    private String capabilities;

    /** 参数护栏 JSON（冻结）。 */
    private String parameterGuardrails;

    /** 上下文长度，单位 token（冻结）。 */
    private Long contextLength;

    /** 加密 API Key 密文（冻结）。 */
    private String encryptedSecret;

    /** 来源草稿 id（软引用）。 */
    private Long sourceDraftId;

    private String remark;

    private Integer isEnabled;
}
