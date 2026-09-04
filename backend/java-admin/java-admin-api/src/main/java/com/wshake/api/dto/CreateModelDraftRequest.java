package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建模型草稿请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "创建模型草稿")
public class CreateModelDraftRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "模型显示名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Schema(description = "OFFICIAL / PRIVATE", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRIVATE")
    private String scope;

    @Size(max = 64)
    @Schema(description = "功能码(video/image 等;普通文本为空)")
    private String code;

    @NotBlank
    @Schema(
            description = "openai-compatible / anthropic",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "openai-compatible")
    private String provider;

    @NotBlank
    @Size(max = 512)
    @Schema(
            description = "HTTPS 连接地址",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "https://api.openai.com/v1")
    private String baseUrl;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "远端模型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "gpt-4o")
    private String modelName;

    @Schema(description = "能力 JSON")
    private String capabilities;

    @Schema(description = "参数护栏 JSON")
    private String parameterGuardrails;

    @Schema(description = "上下文长度，单位 token", example = "500000")
    private Long contextLength;

    @Schema(description = "API Key 明文(落库为密文)")
    private String plainSecret;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
