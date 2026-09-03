package com.wshake.api.vo;

import com.wshake.service.agent.AgentSessionService.AgentSessionView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 会话 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentSessionView.class)
@Schema(description = "Agent 会话")
public class AgentSessionVO {

    private Long id;
    private Long agentDefinitionId;
    private Long agentRevisionId;
    private Long ownerUserId;
    private String status;
    private LocalDateTime lastActiveAt;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
