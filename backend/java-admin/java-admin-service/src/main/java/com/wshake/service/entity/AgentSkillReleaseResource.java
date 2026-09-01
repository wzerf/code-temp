package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** Skill Release 冻结的附属文件。 */
@Data
@Table("agent_skill_release_resource")
@EntityProxy
public class AgentSkillReleaseResource
        implements ProxyEntityAvailable<AgentSkillReleaseResource, AgentSkillReleaseResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long releaseId;
    private String resourcePath;
    private String resourceContent;
    private String contentHash;
    private LocalDateTime createdAt;
}
