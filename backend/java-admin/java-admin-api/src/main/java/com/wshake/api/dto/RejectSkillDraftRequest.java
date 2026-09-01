package com.wshake.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectSkillDraftRequest {

    @Size(max = 512)
    private String comment;
}
