package com.wshake.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkillBindingRequest {

    @NotNull
    private Long skillReleaseId;

    private boolean overrideWinner;
}
