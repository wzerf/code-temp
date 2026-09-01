package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlModels.AgentRevisionView;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = AgentRevisionView.class)
public class AgentRevisionVO {

    private Long id;
    private Long agentDefinitionId;
    private String status;
    private Long sourceDraftRevisionId;
    private String systemPrompt;
    private Map<String, Object> modelConfig;
    private Map<String, Object> permissionPolicy;
    private Map<String, Object> memoryPolicy;
    private Map<String, Object> compressionPolicy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SkillBindingVO> skillBindings;
}
