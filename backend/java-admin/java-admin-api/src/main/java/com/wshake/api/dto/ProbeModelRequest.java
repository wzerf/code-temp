package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建前探测远端模型目录（不落库）。
 *
 * @author wshake
 */
@Data
@Schema(description = "探测模型目录")
public class ProbeModelRequest {

    @NotBlank
    @Schema(description = "openai-compatible / anthropic", requiredMode = Schema.RequiredMode.REQUIRED)
    private String provider;

    @NotBlank
    @Size(max = 512)
    @Schema(description = "HTTPS 连接地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseUrl;

    @Schema(description = "API Key 明文(仅用于本次探测,不落库)")
    private String plainSecret;
}
