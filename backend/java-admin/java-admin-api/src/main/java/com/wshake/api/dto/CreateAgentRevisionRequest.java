package com.wshake.api.dto;

import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
@AutoMapper(target = CreateRevisionCommand.class)
public class CreateAgentRevisionRequest {

    @NotBlank
    @Size(max = 65535)
    private String systemPrompt;

    private Map<String, Object> modelConfig;
    private Map<String, Object> permissionPolicy;
    private Map<String, Object> memoryPolicy;
    private Map<String, Object> compressionPolicy;

    @Size(max = 512)
    private String remark;
}
