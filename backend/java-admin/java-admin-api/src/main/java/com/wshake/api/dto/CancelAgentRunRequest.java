package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelAgentRunRequest {

    @NotBlank
    @Size(max = 128)
    private String requestId;
}
