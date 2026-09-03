package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillGitSourceProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Git Skill 来源配置实体（对齐 {@code agent_skill_git_source}）。
 *
 * @author wshake
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_git_source")
@EntityProxy
public class AgentSkillGitSource extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillGitSource, AgentSkillGitSourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** MARKET / PRIVATE。 */
    private String scope;

    /** 来源所有者。 */
    private Long ownerUserId;

    /** HTTPS 地址（脱敏展示）。 */
    private String url;

    /** 分支/标签/commit。 */
    private String ref;

    /** 仓库子目录。 */
    private String subdirectory;

    /** 加密密钥密文。 */
    private String encryptedSecret;

    /** 最近成功同步 commit。 */
    private String lastCommitSha;

    /** 最近成功同步时间。 */
    private LocalDateTime lastSyncedAt;

    /** READY / FAILED。 */
    private String status;

    /** 错误摘要。 */
    private String lastError;

    private String remark;

    /** 1=启用 0=禁用。 */
    private Integer isEnabled;
}
