package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillGitSourceProxy;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 受控 Git Skill 来源配置；凭据仅保存外部密钥引用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_skill_git_source")
@EntityProxy
public class AgentSkillGitSource extends BaseEntity
        implements ProxyEntityAvailable<AgentSkillGitSource, AgentSkillGitSourceProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String scope;
    private Long ownerUserId;
    private String url;
    private String ref;
    private String subdirectory;
    private String secretRef;
    private String lastCommitSha;
    private LocalDateTime lastSyncedAt;
    private String status;
    private String lastError;
    private Integer isEnabled;
}
