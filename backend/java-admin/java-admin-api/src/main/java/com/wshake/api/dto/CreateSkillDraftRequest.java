package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
public class CreateSkillDraftRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 65535)
    private String description;

    @NotBlank
    private String skillContent;

    @NotBlank
    @Size(max = 32)
    private String visibility;

    private Map<String, String> resources;
    private Long basedOnReleaseId;

    @Size(max = 512)
    private String remark;
}
