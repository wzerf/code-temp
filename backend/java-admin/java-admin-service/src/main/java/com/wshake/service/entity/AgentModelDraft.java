package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentModelDraftProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型连接配置草稿实体（对齐 {@code agent_model_draft}）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_model_draft")
@EntityProxy
public class AgentModelDraft extends BaseEntity implements ProxyEntityAvailable<AgentModelDraft, AgentModelDraftProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所有者（软引用 sys_user.id；OFFICIAL 可为 0=平台）。 */
    private Long ownerUserId;

    /** 模型显示名（唯一键内）。 */
    private String name;

    /** OFFICIAL / PRIVATE。 */
    private String scope;

    /** 功能码（video/image 等；普通文本为空）。 */
    private String code;

    /** DRAFT / PENDING_REVIEW / REJECTED / CONSUMED。 */
    private String status;

    /** openai-compatible / anthropic（小写）。 */
    private String provider;

    /** 连接地址（HTTPS）。 */
    private String baseUrl;

    /** 远端模型标识。 */
    private String modelName;

    /** 能力 JSON。 */
    private String capabilities;

    /** 参数护栏 JSON。 */
    private String parameterGuardrails;

    /** 上下文长度，单位 token。 */
    private Long contextLength;

    /** 加密 API Key 密文（不存明文）。 */
    private String encryptedSecret;

    /** 审核意见（对用户可见）。 */
    private String reviewComment;

    /** 审核人（0=未审）。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    private String remark;

    private Integer isEnabled;
}
