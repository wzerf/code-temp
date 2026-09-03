package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Skill Release 附属资源实体（对齐 {@code agent_skill_release_resource}）。
 *
 * <p>子资源表：冻结，随 Release 物理删除，无软删字段。
 *
 * @author wshake
 */
@Data
@Table("agent_skill_release_resource")
@EntityProxy
public class AgentSkillReleaseResource
        implements ProxyEntityAvailable<AgentSkillReleaseResource, AgentSkillReleaseResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Release。 */
    private Long releaseId;

    /** 相对路径。 */
    private String resourcePath;

    /** 资源内容。 */
    private String content;

    private LocalDateTime createdAt;
}
