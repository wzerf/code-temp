package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGitSkillSourceRequest {

    @NotBlank
    @Size(max = 16)
    private String scope;

    @NotBlank
    @Size(max = 2048)
    private String url;

    @Size(max = 255)
    private String ref;

    @Size(max = 500)
    private String subdirectory;

    @Size(max = 255)
    private String secretRef;
}
