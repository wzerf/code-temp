package com.wshake.service.port;

import com.wshake.service.agent.mcp.McpManageModels.McpToolEntry;
import java.util.List;
import java.util.Map;

/**
 * MCP 握手探针端口：验证连接并返回工具目录（由 infra 用 AgentScope McpClientBuilder 实现）。
 *
 * <p>业务层只依赖本接口，不直接耦合 MCP SDK，便于单测 mock。
 *
 * @author wshake
 */
public interface McpProbePort {

    /**
     * 握手并拉取工具目录。
     *
     * @param transport    sse / http
     * @param url          连接地址
     * @param headers      静态头（无密；明文密钥在调用前已解密注入）
     * @param timeoutMs    连接超时（毫秒）
     * @return 工具目录
     */
    List<McpToolEntry> probe(String transport, String url, Map<String, String> headers, int timeoutMs);
}
