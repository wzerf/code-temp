package com.wshake.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

@Getter
public class UpdateGitSkillSourceRequest {

    private String url;
    private String ref;
    private String subdirectory;
    private String secretRef;
    @JsonIgnore private boolean urlPresent;
    @JsonIgnore private boolean refPresent;
    @JsonIgnore private boolean subdirectoryPresent;
    @JsonIgnore private boolean secretRefPresent;

    public void setUrl(String url) { this.url = url; this.urlPresent = true; }
    public void setRef(String ref) { this.ref = ref; this.refPresent = true; }
    public void setSubdirectory(String subdirectory) { this.subdirectory = subdirectory; this.subdirectoryPresent = true; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; this.secretRefPresent = true; }
}
