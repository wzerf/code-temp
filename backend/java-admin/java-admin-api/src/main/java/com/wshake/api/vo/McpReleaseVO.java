package com.wshake.api.vo;

import com.wshake.service.mcp.McpControlService.McpReleaseView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Release VO（不含密钥;hasSecret 仅标记是否有密钥）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = McpReleaseView.class)
@Schema(description = "MCP Release")
public class McpReleaseVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String visibility;
    private String status;
    private Integer version;
    private String transport;
    private String url;
    private String headersJson;
    private Boolean hasSecret;
    private Integer connectTimeoutMs;
    private Long sourceDraftId;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    @Schema(description = "认证方式：NONE=静态密钥直连；OAUTH=OAuth 登录")
    private String authType;

    @Schema(description = "OAuth Client ID（冻结）")
    private String oauthClientId;

    @Schema(description = "OAuth scope（冻结）")
    private String oauthScope;

    @Schema(description = "OAuth 授权端点（冻结）")
    private String oauthAuthorizationEndpoint;

    @Schema(description = "OAuth 换票端点（冻结）")
    private String oauthTokenEndpoint;

    @Schema(description = "是否已配 OAuth Client Secret（仅标记；MARKET 恒为 false）")
    private Boolean hasOauthClientSecret;
}
