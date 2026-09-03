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

    /** DRAFT=草稿 / PUBLISHED=已发布不可变。 */
    private String status;

    /** 发布快照来源草稿 Revision id（仅 PUBLISHED 行有）。 */
    private Long sourceDraftRevisionId;

    /** 系统提示词快照。 */
    private String systemPrompt;

    /** 模型配置 JSON 快照。 */
    private String modelConfig;

    /** 运行时权限策略 JSON。 */
    private String permissionPolicy;

    /** 记忆策略（首期非空即拒绝运行）。 */
    private String memoryPolicy;

    /** 压缩策略（首期非空即拒绝运行）。 */
    private String compressionPolicy;

    private String remark;

    private Integer isEnabled;
}
