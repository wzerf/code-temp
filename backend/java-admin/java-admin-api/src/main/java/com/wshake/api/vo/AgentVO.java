package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlService.AgentDefinitionView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 定义 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentDefinitionView.class)
@Schema(description = "Agent 定义")
public class AgentVO {

    private Long id;
    private String name;
    private String description;
    private Long ownerUserId;
    private Long currentPublishedRevisionId;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
