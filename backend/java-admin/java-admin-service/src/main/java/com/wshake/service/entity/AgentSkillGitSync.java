package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillGitSyncProxy;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Git Skill 同步记录实体（对齐 {@code agent_skill_git_sync}）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_skill_git_sync")
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

    /** 对应草稿（软引用；NULL=无草稿）。 */
    private Long draftId;

    /** CREATED / UPDATED / UNCHANGED / CONFLICT / FAILED。 */
    private String result;

    /** 软删毫秒时间戳。 */
    private Long deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
