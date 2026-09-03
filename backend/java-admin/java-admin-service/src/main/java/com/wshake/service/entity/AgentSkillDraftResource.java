package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillDraftResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Skill 草稿资源文件实体（对齐 {@code agent_skill_draft_resource}）。
 *
 * <p>资源为子表：仅 {@code created_at},无软删/审计/启停,随草稿全量重写。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_skill_draft_resource")
public class AgentSkillDraftResource
        implements ProxyEntityAvailable<AgentSkillDraftResource, AgentSkillDraftResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属草稿。 */
    private Long draftId;

    /** 相对路径（如 references/foo.md）。 */
    private String resourcePath;

    /** 文件文本内容。 */
    private String content;

    /** 文件内容 SHA-256 hex。 */
    private String contentHash;

    private LocalDateTime createdAt;
}
