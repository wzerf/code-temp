package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定 MCP Release 到 Agent Revision 请求。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定 MCP 到 Revision")
public class BindMcpRequest {

    @NotNull
    @Schema(description = "MCP Release id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long mcpReleaseId;

    @NotNull
    @Schema(description = "server 名(从 Release 拷贝)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mcpName;

    @Schema(description = "密钥明文(MARKET MCP 绑定到 Agent 时在此补配;PRIVATE 可沿用或覆盖;落库为密文)")
    private String plainSecret;
}
