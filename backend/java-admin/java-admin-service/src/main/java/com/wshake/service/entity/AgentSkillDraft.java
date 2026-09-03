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

    /** 所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** Skill 名（来自 SKILL.md frontmatter name）。 */
    private String name;

    /** MARKET=进市场 / PRIVATE=仅所有者。 */
    private String visibility;

    /** DRAFT / PENDING_REVIEW / REJECTED / CONSUMED。 */
    private String status;

    /** 来自 SKILL.md frontmatter description。 */
    private String description;

    /** 完整 SKILL.md 全文。 */
    private String skillContent;

    /** SKILL.md+资源按 resource_path 字典序拼接的 SHA-256 hex。 */
    private String contentHash;

    /** 从既有 Release 开草稿时的来源（软引用）。 */
    private Long basedOnReleaseId;

    /** 审核意见（对用户可见）。 */
    private String reviewComment;

    /** 审核人（0=未审）。 */
    private Long reviewedBy;

    /** 审核时间。 */
    private LocalDateTime reviewedAt;

    private String remark;

    private Integer isEnabled;
}
