package com.wshake.infra.agent.mcp;

import com.wshake.service.agent.mcp.McpManageModels.McpToolEntry;
import com.wshake.service.port.McpProbePort;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * MCP 握手探针实现（基于 AgentScope {@link McpClientBuilder}）。
 *
 * <p>支持 sse / http（streamableHttp）。静态头无密；明文密钥在调用前已解密注入。
 *
 * @author wshake
 */
@Service
public class McpProbeService implements McpProbePort {

    @Override
    public List<McpToolEntry> probe(String transport, String url, Map<String, String> headers, int timeoutMs) {
        McpClientBuilder builder = McpClientBuilder.create("probe");
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
        builder.timeout(Duration.ofMillis(timeoutMs));
        builder.initializationTimeout(Duration.ofMillis(timeoutMs));

        if ("sse".equals(transport)) {
            builder.sseTransport(url);
        } else {
            builder.streamableHttpTransport(url);
        }

        try (McpClientWrapper client = builder.buildSync()) {
            client.initialize().block();
            List<io.modelcontextprotocol.spec.McpSchema.Tool> tools =
                    client.listTools().block();
            if (tools == null) {
                return List.of();
            }
            List<McpToolEntry> entries = new ArrayList<>();
            for (io.modelcontextprotocol.spec.McpSchema.Tool tool : tools) {
                entries.add(new McpToolEntry(
                        tool.name(),
                        tool.description() == null ? "" : tool.description(),
                        convertSchema(tool.inputSchema()),
                        false));
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException("MCP probe failed: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> convertSchema(io.modelcontextprotocol.spec.McpSchema.JsonSchema schema) {
        if (schema == null || schema.properties() == null) {
            return Map.of();
        }
        return schema.properties();
    }
}
