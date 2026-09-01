package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillDraftProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Skill 草稿与审核中内容。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_draft")
@EntityProxy
public class AgentSkillDraft extends BaseEntity implements ProxyEntityAvailable<AgentSkillDraft, AgentSkillDraftProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String name;
    private String description;
    private String skillContent;
    private String visibility;
    private String status;
    private Long ownerUserId;
    private Long basedOnReleaseId;
    private String contentHash;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String remark;
    private Integer isEnabled;
}
