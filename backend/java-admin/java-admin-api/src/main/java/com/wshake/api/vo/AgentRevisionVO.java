package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlService.AgentRevisionView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Revision VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentRevisionView.class)
@Schema(description = "Agent Revision")
public class AgentRevisionVO {

    private Long id;
    private Long agentDefinitionId;
    private String status;
    private Long sourceDraftRevisionId;
    private String systemPrompt;
    private String modelConfig;
    private String permissionPolicy;
    private String memoryPolicy;
    private String compressionPolicy;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
