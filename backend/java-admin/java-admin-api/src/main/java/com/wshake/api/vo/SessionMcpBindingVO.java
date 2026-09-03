package com.wshake.api.vo;

import com.wshake.service.agent.AgentSessionService.SessionMcpBindingView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Session MCP 绑定 VO（hasSecret 仅标记是否配密钥,不泄露密文）。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SessionMcpBindingView.class)
@Schema(description = "会话 MCP 绑定")
public class SessionMcpBindingVO {

    private Long id;
    private Long sessionId;
    private Long mcpReleaseId;
    private String mcpName;
    private Boolean hasSecret;
}
