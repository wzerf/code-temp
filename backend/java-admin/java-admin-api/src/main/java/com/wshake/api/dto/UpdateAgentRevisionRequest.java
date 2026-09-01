package com.wshake.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** 字段出现语义：省略不更新，显式 null 清空可选 JSON/备注字段。 */
@Getter
public class UpdateAgentRevisionRequest {

    private String systemPrompt;

    @JsonIgnore
    private boolean systemPromptPresent;

    private Map<String, Object> modelConfig;

    @JsonIgnore
    private boolean modelConfigPresent;

    private Map<String, Object> permissionPolicy;

    @JsonIgnore
    private boolean permissionPolicyPresent;

    private Map<String, Object> memoryPolicy;

    @JsonIgnore
    private boolean memoryPolicyPresent;

    private Map<String, Object> compressionPolicy;

    @JsonIgnore
    private boolean compressionPolicyPresent;

    private String remark;

    @JsonIgnore
    private boolean remarkPresent;

    private List<SkillBindingRequest> skillBindings;

    @JsonIgnore
    private boolean skillBindingsPresent;

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        this.systemPromptPresent = true;
    }

    public void setModelConfig(Map<String, Object> modelConfig) {
        this.modelConfig = modelConfig;
        this.modelConfigPresent = true;
    }

    public void setPermissionPolicy(Map<String, Object> permissionPolicy) {
        this.permissionPolicy = permissionPolicy;
        this.permissionPolicyPresent = true;
    }

    public void setMemoryPolicy(Map<String, Object> memoryPolicy) {
        this.memoryPolicy = memoryPolicy;
        this.memoryPolicyPresent = true;
    }

    public void setCompressionPolicy(Map<String, Object> compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
        this.compressionPolicyPresent = true;
    }

    public void setRemark(String remark) {
        this.remark = remark;
        this.remarkPresent = true;
    }

    public void setSkillBindings(List<SkillBindingRequest> skillBindings) {
        this.skillBindings = skillBindings;
        this.skillBindingsPresent = true;
    }
}
