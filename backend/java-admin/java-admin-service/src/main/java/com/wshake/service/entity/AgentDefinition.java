package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentDefinitionProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 定义实体（对齐 {@code agent_definition}）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_definition")
@EntityProxy
public class AgentDefinition extends BaseEntity implements ProxyEntityAvailable<AgentDefinition, AgentDefinitionProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 名称（软删感知唯一）。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 所有者（软引用 sys_user.id；0=平台）。 */
    private Long ownerUserId;

    /** 当前发布 Revision 指针（首次发布前 NULL）。 */
    private Long currentPublishedRevisionId;

    private String remark;

    /** 1=启用 0=禁用（紧急禁用用此列）。 */
    private Integer isEnabled;
}
