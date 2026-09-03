package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 MCP 草稿请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "创建 MCP 草稿")
public class CreateMcpDraftRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "server 名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank
    @Schema(description = "sse / http(小写)", requiredMode = Schema.RequiredMode.REQUIRED, example = "http")
    private String transport;

    @NotBlank
    @Size(max = 512)
    @Schema(description = "连接地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://mcp.example.com/mcp")
    private String url;

    @Schema(description = "静态头(无密);JSON 字符串字典", example = "{\"Accept\":\"application/json\"}")
    private String headersJson;

    @NotBlank
    @Schema(description = "MARKET / PRIVATE", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRIVATE")
    private String visibility;

    @Schema(description = "密钥明文(仅 PRIVATE 草稿可配;MARKET 传入将被忽略;落库为密文)")
    private String plainSecret;

    @Schema(description = "连接超时毫秒,默认 5000")
    private Integer connectTimeoutMs;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
