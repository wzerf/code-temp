package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新 MCP 草稿请求（字段 null 表示不改）。
 *
 * @author wshake
 */
@Data
@Schema(description = "更新 MCP 草稿")
public class UpdateMcpDraftRequest {

    @Size(max = 128)
    @Schema(description = "server 名")
    private String name;

    @Schema(description = "sse / http(小写)")
    private String transport;

    @Size(max = 512)
    @Schema(description = "连接地址")
    private String url;

    @Schema(description = "静态头(无密);JSON 字符串字典")
    private String headersJson;

    @Schema(description = "密钥明文(仅 PRIVATE;传 null 表示不改)")
    private String plainSecret;

    @Schema(description = "连接超时毫秒")
    private Integer connectTimeoutMs;

    @Size(max = 512)
    @Schema(description = "备注")
    private String remark;
}
