package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlService.McpBindingView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Revision MCP 绑定 VO（hasSecret 仅标记是否配密钥,不泄露密文）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = McpBindingView.class)
@Schema(description = "Revision MCP 绑定")
public class RevisionMcpBindingVO {

    private Long id;
    private Long agentRevisionId;
    private Long mcpReleaseId;
    private String mcpName;
    private Boolean hasSecret;
}
