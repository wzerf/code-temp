package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlModels.AgentDefinitionView;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentDefinitionView.class)
public class AgentDefinitionVO {

    private Long id;
    private String name;
    private Long ownerUserId;
    private String description;
    private Long currentPublishedRevisionId;
    private String remark;
    private Integer isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
