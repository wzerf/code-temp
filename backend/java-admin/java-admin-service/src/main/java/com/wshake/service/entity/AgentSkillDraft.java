package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillDraftProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill 草稿实体（对齐 {@code agent_skill_draft}）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_draft")
@EntityProxy
public class AgentSkillDraft extends BaseEntity implements ProxyEntityAvailable<AgentSkillDraft, AgentSkillDraftProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** Skill 名。 */
    private String name;

    /** 完整 SKILL.md。 */
    private String skillContent;

    /** MARKET / PRIVATE。 */
    private String visibility;

    /** DRAFT / PENDING_REVIEW / REJECTED / CONSUMED。 */
    private String status;

    /** 所有者。 */
    private Long ownerUserId;

    /** 从既有 Release 开草稿时的来源。 */
    private Long basedOnReleaseId;

    /** 内容 hash。 */
    private String contentHash;

    /** 审核意见。 */
    private String reviewComment;

    /** 审核人。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
