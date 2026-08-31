package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlModels.AgentSessionView;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentSessionView.class)
public class AgentSessionVO {

    private Long id;
    private Long agentDefinitionId;
    private Long agentRevisionId;
    private Long ownerUserId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
}
