package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSkillMarketResourceProxy;
import java.time.LocalDateTime;
import lombok.Data;

/** 市场当前行附属文件；复合主键，级联物理删除。 */
@Data
@Table("agent_skill_resource")
@EntityProxy
public class AgentSkillMarketResource
        implements ProxyEntityAvailable<AgentSkillMarketResource, AgentSkillMarketResourceProxy> {

    @Column(primaryKey = true)
    private Long id;

    @Column(primaryKey = true)
    private String resourcePath;

    private String resourceContent;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
