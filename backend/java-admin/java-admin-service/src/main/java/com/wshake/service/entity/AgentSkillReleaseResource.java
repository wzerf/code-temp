package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillReleaseResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Skill Release 冻结资源文件实体（对齐 {@code agent_skill_release_resource}）。
 *
 * <p>不可变,仅 {@code created_at}。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_skill_release_resource")
public class AgentSkillReleaseResource
        implements ProxyEntityAvailable<AgentSkillReleaseResource, AgentSkillReleaseResourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属 Release。 */
    private Long releaseId;

    /** 相对路径。 */
    private String resourcePath;

    /** 文件文本内容。 */
    private String content;

    /** 文件内容 SHA-256 hex。 */
    private String contentHash;

    private LocalDateTime createdAt;
}
