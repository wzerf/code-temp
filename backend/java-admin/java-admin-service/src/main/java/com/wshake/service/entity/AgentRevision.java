package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent Revision 实体（对齐 {@code agent_revision}）。
 *
 * <p>{@code modelConfig} / {@code permissionPolicy} / {@code memoryPolicy} /
 * {@code compressionPolicy} 以 JSON 字符串落库，读写由业务层序列化。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_revision")
@EntityProxy
public class AgentRevision extends BaseEntity implements ProxyEntityAvailable<AgentRevision, AgentRevisionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Definition。 */
    private Long agentDefinitionId;

    /** DRAFT / PUBLISHED。 */
    private String status;

    /** 发布快照来源草稿。 */
    private Long sourceDraftRevisionId;

    /** 系统提示词。 */
    private String systemPrompt;

    /** 模型配置 JSON 快照。 */
    private String modelConfig;

    /** 权限策略 JSON 快照。 */
    private String permissionPolicy;

    /** 记忆策略 JSON 快照（可空）。 */
    private String memoryPolicy;

    /** 压缩策略 JSON 快照（可空）。 */
    private String compressionPolicy;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
