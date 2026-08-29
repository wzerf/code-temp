package com.wshake.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RollbackAgentRevisionRequest {

    @NotNull
    private Long revisionId;
}
