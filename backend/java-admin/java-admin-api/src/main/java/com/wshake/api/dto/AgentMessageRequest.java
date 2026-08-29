package com.wshake.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageRequest {

    @NotBlank
    @Size(max = 128)
    private String requestId;

    @NotBlank
    @Size(max = 65535)
    private String message;
}
