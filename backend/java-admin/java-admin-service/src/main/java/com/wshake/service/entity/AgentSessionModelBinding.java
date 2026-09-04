package com.wshake.service.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import com.wshake.service.entity.proxy.AgentSessionModelBindingProxy;
import lombok.Data;

/**
 * Agent Session 模型选择（对齐 {@code agent_session_model_binding}，无软删，解绑=物理删）。
 *
 * @author wshake
 */
@Data
@EntityProxy
@Table("agent_session_model_binding")
public class AgentSessionModelBinding
        implements ProxyEntityAvailable<AgentSessionModelBinding, AgentSessionModelBindingProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    /** 归属会话。 */
    private Long sessionId;

    /** 用户选择的模型 Release 指针。 */
    private Long modelReleaseId;

    /** 从 Release 拷贝的远端模型标识。 */
    private String modelName;

    /** 创建人（0=系统操作）。 */
    private Long createdBy;
}
