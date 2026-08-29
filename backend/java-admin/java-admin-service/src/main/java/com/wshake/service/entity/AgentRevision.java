package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentRevisionProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 草稿或不可变发布 Revision。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_revision")
@EntityProxy
public class AgentRevision extends BaseEntity implements ProxyEntityAvailable<AgentRevision, AgentRevisionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long agentDefinitionId;
    private String status;
    private Long sourceDraftRevisionId;
    private String systemPrompt;
    private String modelConfig;
    private String permissionPolicy;
    private String memoryPolicy;
    private String compressionPolicy;
    private String remark;
    private Integer isEnabled;
}
