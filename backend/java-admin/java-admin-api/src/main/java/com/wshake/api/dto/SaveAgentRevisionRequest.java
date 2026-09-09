package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent Revision 草稿保存请求（创建/更新共用;null 表示不改）。
 *
 * @author wshake
 */
@Data
@Schema(description = "Agent Revision 草稿保存")
public class SaveAgentRevisionRequest {

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "模型配置 JSON 文本(快照)")
    private String modelConfig;

    @Schema(description = "运行时权限策略 JSON(permission_policy.allowedTools 白名单)")
    private String permissionPolicy;

    @Schema(description = "记忆策略 JSON(首期非空即拒绝运行)")
    private String memoryPolicy;

    @Schema(description = "压缩策略 JSON(首期非空即拒绝运行)")
    private String compressionPolicy;

    @Schema(description = "生图 Provider（写入 model_config.image.provider）")
    private String imageProvider;

    @Schema(description = "生图 BaseUrl（写入 model_config.image.base_url，HTTPS）")
    private String imageBaseUrl;

    @Schema(description = "生图模型名（写入 model_config.image.model_name）")
    private String imageModelName;

    @Schema(description = "生图明文密钥（加密后写入 model_config.image.encrypted_secret）")
    private String imagePlainSecret;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
