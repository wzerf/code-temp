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
 * Git Skill 来源实体（对齐 {@code agent_skill_git_source},受控导入配置）。
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

    /** MARKET（仅管理员）/ PRIVATE（归当前用户）。 */
    private String scope;

    /** 来源所有者（软引用 sys_user.id）。 */
    private Long ownerUserId;

    /** HTTPS 地址（禁止 SSH/本地路径/user-info；展示脱敏）。 */
    private String url;

    /** 分支/标签/commit。 */
    private String ref;

    /** 仓库子目录。 */
    private String subdirectory;

    /** 加密密钥密文（私有仓库用）。 */
    private String encryptedSecret;

    /** 最近成功同步 commit_sha。 */
    private String lastCommitSha;

    /** 最近成功同步时间。 */
    private LocalDateTime lastSyncedAt;

    /** READY=正常 / FAILED=同步失败。 */
    private String status;

    /** 错误摘要。 */
    private String lastError;

    private String remark;

    private Integer isEnabled;
}
