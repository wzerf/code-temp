package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSkillDraftResourceRequest {

    @NotBlank
    @Size(max = 500)
    private String path;

    private String content;
}
