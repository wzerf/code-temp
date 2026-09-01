package com.wshake.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import lombok.Getter;

@Getter
public class UpdateSkillDraftRequest {

    private String description;

    @JsonIgnore
    private boolean descriptionPresent;

    private String skillContent;

    @JsonIgnore
    private boolean skillContentPresent;

    private Map<String, String> resources;

    @JsonIgnore
    private boolean resourcesPresent;

    private String remark;

    @JsonIgnore
    private boolean remarkPresent;

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public void setSkillContent(String skillContent) {
        this.skillContent = skillContent;
        this.skillContentPresent = true;
    }

    public void setResources(Map<String, String> resources) {
        this.resources = resources;
        this.resourcesPresent = true;
    }

    public void setRemark(String remark) {
        this.remark = remark;
        this.remarkPresent = true;
    }
}
