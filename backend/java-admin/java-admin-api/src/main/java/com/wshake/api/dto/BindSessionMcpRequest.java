package com.wshake.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 绑定 MCP Release 到 Agent 会话请求（用户侧追加/覆盖;可补配密钥）。
 *
 * @author wshake
 */
@Data
@Schema(description = "绑定 MCP 到会话")
public class BindSessionMcpRequest {

    @NotNull
    @Schema(description = "MCP Release id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long mcpReleaseId;

    @NotNull
    @Schema(description = "server 名(Session 内唯一;同名覆盖 Revision)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mcpName;

    @Schema(description = "密钥明文(MARKET MCP 在此补配;落库为密文)")
    private String plainSecret;
}
