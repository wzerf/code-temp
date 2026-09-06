package com.wshake.api.vo;

import com.wshake.service.mcp.McpControlService.McpDraftView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 草稿 VO（不含密钥密文）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = McpDraftView.class)
@Schema(description = "MCP 草稿")
public class McpDraftVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String visibility;
    private String status;
    private String transport;
    private String url;
    private String headersJson;
    private Integer connectTimeoutMs;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    @Schema(description = "认证方式：NONE=静态密钥直连；OAUTH=OAuth 登录")
    private String authType;

    @Schema(description = "OAuth Client ID")
    private String oauthClientId;

    @Schema(description = "OAuth scope")
    private String oauthScope;

    @Schema(description = "OAuth 授权端点（发现缓存）")
    private String oauthAuthorizationEndpoint;

    @Schema(description = "OAuth 换票端点（发现缓存）")
    private String oauthTokenEndpoint;

    @Schema(description = "MARKET 发布是否要求校验发布者登录态；0=可选跳过")
    private Integer oauthRequireLogin;

    @Schema(description = "是否已配 OAuth Client Secret（仅标记）")
    private Boolean hasOauthClientSecret;
}
