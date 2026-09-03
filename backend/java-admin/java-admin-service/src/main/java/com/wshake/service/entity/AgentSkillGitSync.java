package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillGitSyncProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Git 幂等同步记录实体（对齐 {@code agent_skill_git_sync}）。
 *
 * <p>唯一键 {@code (source_id, skill_path, deleted_at)} 保护人工修改并支持幂等同步。
 *
 * @author wshake
 */
@Data
@Table("agent_skill_git_sync")
@EntityProxy
public class AgentSkillGitSync implements ProxyEntityAvailable<AgentSkillGitSync, AgentSkillGitSyncProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 来源。 */
    private Long sourceId;

    /** 同步时 commit。 */
    private String commitSha;

    /** 包路径。 */
    private String skillPath;

    /** 导入内容 hash。 */
    private String contentHash;

    /** 对应草稿。 */
    private Long draftId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删时间戳；0=未删。 */
    private Long deletedAt;
}
