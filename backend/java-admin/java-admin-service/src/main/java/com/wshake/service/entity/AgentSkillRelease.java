package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseProxy;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Skill Release 实体（对齐 {@code agent_skill_release}）。
 *
 * <p>不可变快照；市场列表由此派生。
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

    /** 所有者。 */
    private Long ownerUserId;

    /** Skill 名。 */
    private String name;

    /** MARKET / PRIVATE。 */
    private String visibility;

    /** 在 (owner, visibility, name) 内从 1 递增。 */
    private Integer version;

    /** PUBLISHED / DEPRECATED。 */
    private String status;

    /** 来源草稿。 */
    private Long sourceDraftId;

    /** 冻结 SKILL.md。 */
    private String skillContent;

    /** 冻结内容 hash。 */
    private String contentHash;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
