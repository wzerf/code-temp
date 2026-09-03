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
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
