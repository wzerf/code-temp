package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 同一连接配置下批量创建模型草稿。
 *
 * @author wshake
 */
@Data
@Schema(description = "批量创建模型草稿")
public class BatchCreateModelDraftRequest {

    @NotBlank
    @Schema(description = "OFFICIAL / PRIVATE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    @NotBlank
    @Schema(description = "openai-compatible / anthropic", requiredMode = Schema.RequiredMode.REQUIRED)
    private String provider;

    @NotBlank
    @Size(max = 512)
    @Schema(description = "HTTPS 连接地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseUrl;

    @Schema(description = "API Key 明文(落库为密文,各草稿共用)")
    private String plainSecret;

    @Schema(description = "共享能力 JSON;条目未单独指定时使用")
    private String capabilities;

    @Schema(description = "共享参数护栏 JSON")
    private String parameterGuardrails;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;

    @Valid
    @NotEmpty
    @Schema(description = "要创建的模型条目", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> items;

    /** 单条草稿。 */
    @Data
    @Schema(description = "批量创建条目")
    public static class Item {

        @Size(max = 128)
        @Schema(description = "显示名;空则用 modelName")
        private String name;

        @NotBlank
        @Size(max = 128)
        @Schema(description = "远端模型标识", requiredMode = Schema.RequiredMode.REQUIRED)
        private String modelName;

        @Size(max = 64)
        @Schema(description = "功能码")
        private String code;

        @Schema(description = "覆盖共享能力 JSON")
        private String capabilities;

        @Schema(description = "覆盖共享护栏 JSON")
        private String parameterGuardrails;

        @Schema(description = "上下文长度，单位 token", example = "500000")
        private Long contextLength;
    }
}
