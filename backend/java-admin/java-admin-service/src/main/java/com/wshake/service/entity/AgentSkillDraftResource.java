package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillDraftResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** Skill 草稿附属文件。 */
@Data
@Table("agent_skill_draft_resource")
@EntityProxy
public class AgentSkillDraftResource
        implements ProxyEntityAvailable<AgentSkillDraftResource, AgentSkillDraftResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private Long draftId;
    private String resourcePath;
    private String resourceContent;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
