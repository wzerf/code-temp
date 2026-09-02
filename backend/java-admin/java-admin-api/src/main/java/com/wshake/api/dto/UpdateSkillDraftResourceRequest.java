package com.wshake.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateSkillDraftResourceRequest {

    @Size(max = 500)
    private String path;

    @JsonIgnore
    private boolean pathPresent;

    private String content;

    @JsonIgnore
    private boolean contentPresent;

    public void setPath(String path) {
        this.path = path;
        this.pathPresent = true;
    }

    public void setContent(String content) {
        this.content = content;
        this.contentPresent = true;
    }
}
