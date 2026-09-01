package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.annotation.UpdateIgnore;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillMarketProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** SDK 市场当前已发布行；无软删，下架=物理 DELETE。 */
@Data
@Table("agent_skill")
@EntityProxy
public class AgentSkillMarket implements ProxyEntityAvailable<AgentSkillMarket, AgentSkillMarketProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    private String name;
    private String description;
    private String skillContent;
    private String source;
    private String metadataJson;
    private Long currentReleaseId;
    private Long ownerUserId;
    private String visibility;
    private String contentHash;
    private String remark;
    private Integer isEnabled;
    private Long createdBy;
    private Long updatedBy;

    @UpdateIgnore
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
