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
}
