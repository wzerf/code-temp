package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentDefinitionProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 的稳定业务标识与当前发布 Revision 指针。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_definition")
@EntityProxy
public class AgentDefinition extends BaseEntity implements ProxyEntityAvailable<AgentDefinition, AgentDefinitionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String name;
    private String description;
    private Long ownerUserId;
    private Long currentPublishedRevisionId;
    private String remark;
    private Integer isEnabled;
}
