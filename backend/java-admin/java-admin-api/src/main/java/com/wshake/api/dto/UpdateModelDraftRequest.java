package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新模型草稿请求（字段 null 表示不改）。
 *
 * @author wshake
 */
@Data
@Schema(description = "更新模型草稿")
public class UpdateModelDraftRequest {

    @Size(max = 128)
    @Schema(description = "模型显示名")
    private String name;

    @Size(max = 64)
    @Schema(description = "功能码")
    private String code;

    @Schema(description = "openai-compatible / anthropic")
    private String provider;

    @Size(max = 512)
    @Schema(description = "HTTPS 连接地址")
    private String baseUrl;

    @Size(max = 128)
    @Schema(description = "远端模型标识")
    private String modelName;

    @Schema(description = "能力 JSON")
    private String capabilities;

    @Schema(description = "参数护栏 JSON")
    private String parameterGuardrails;

    @Schema(description = "上下文长度，单位 token")
    private Long contextLength;

    @Schema(description = "API Key 明文(传 null 表示不改)")
    private String plainSecret;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
