package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InstallSkillRequest {

    @NotBlank
    @Size(max = 255)
    private String name;
}
