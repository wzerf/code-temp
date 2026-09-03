package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillDraftResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Skill 草稿附属资源实体（对齐 {@code agent_skill_draft_resource}）。
 *
 * <p>子资源表：随草稿物理删除，无软删字段。
 *
 * @author wshake
 */
@Data
@Table("agent_skill_draft_resource")
@EntityProxy
public class AgentSkillDraftResource
        implements ProxyEntityAvailable<AgentSkillDraftResource, AgentSkillDraftResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属草稿。 */
    private Long draftId;

    /** 相对路径，禁止 .. / 绝对路径 / 反斜杠。 */
    private String resourcePath;

    /** 资源内容。 */
    private String content;

    private LocalDateTime createdAt;
}
