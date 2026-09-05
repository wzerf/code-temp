package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 握手验证结果 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 握手验证结果")
public class McpVerifyResultVO {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "工具数量")
    private Integer toolCount;

    @Schema(description = "工具列表")
    private List<McpToolEntryVO> tools;

    @Schema(description = "OAuth 授权端点（需登录时返回）")
    private String oauthAuthorizationUrl;

    @Schema(description = "OAuth scope")
    private String oauthScope;

    @Schema(description = "OAuth 受保护资源标识")
    private String oauthResource;

    @Schema(description = "OAuth Protected Resource Metadata URL")
    private String oauthResourceMetadataUrl;

    /** 工具条目。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "MCP 工具")
    public static class McpToolEntryVO {
        private String name;
        private String description;
        private String inputSchema;
        private Boolean readOnly;
    }
}
