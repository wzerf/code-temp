package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill Release 实体（对齐 {@code agent_skill_release},不可变快照）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_release")
@EntityProxy
public class AgentSkillRelease extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillRelease, AgentSkillReleaseProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** Skill 名（冻结）。 */
    private String name;

    /** MARKET / PRIVATE（冻结；只决定是否进市场列表）。 */
    private String visibility;

    /** PUBLISHED=在售 / DEPRECATED=弃用。 */
    private String status;

    /** 在 (owner,visibility,name) 内从 1 递增。 */
    private Integer version;

    /** 来自 SKILL.md description（冻结）。 */
    private String description;

    /** 完整 SKILL.md 全文（冻结,不可 UPDATE）。 */
    private String skillContent;

    /** 冻结内容 hash（运行漂移校验用）。 */
    private String contentHash;

    /** 来源草稿 id（软引用）。 */
    private Long sourceDraftId;

    private String remark;

    private Integer isEnabled;
}
