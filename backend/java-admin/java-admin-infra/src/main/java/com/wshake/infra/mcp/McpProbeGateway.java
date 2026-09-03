package com.wshake.infra.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.service.port.McpProbePort;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP 握手探测实现（service {@link McpProbePort} 的官方 Java SDK 适配）。
 *
 * <p>基于 {@code io.modelcontextprotocol.sdk} 2.x 的 Streamable HTTP 客户端:
 * SDK 负责 JSON-RPC {@code initialize} + {@code tools/list}、text/event-stream 帧解析,
 * 并兼容 202+text/plain 空响应(streamable HTTP 服务如 Firecrawl 的行为)。
 * 握手失败/目录为空由调用方按「未知即拒绝」处理。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class McpProbeGateway implements McpProbePort {

    private final ObjectMapper objectMapper;

    @Override
    public List<McpToolEntry> probe(ProbeCommand command) {
        Map<String, String> headers = command.headers();
        int timeoutMs = command.connectTimeout() > 0 ? command.connectTimeout() : 5000;

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(command.url())
                // 每次请求注入静态头(解密后的 Authorization 等),明文只在本方法内存
                .httpRequestCustomizer((builder, method, endpoint, body, context) -> {
                    if (headers != null) {
                        headers.forEach(builder::header);
                    }
                })
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(
                        McpSchema.Implementation.builder("java-admin", "1.0").build())
                .requestTimeout(Duration.ofMillis(timeoutMs))
                .build()) {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpSchema.Tool> tools = result.tools() == null ? List.of() : result.tools();
            return mapTools(tools);
        }
    }

    private List<McpToolEntry> mapTools(List<McpSchema.Tool> tools) {
        List<McpToolEntry> entries = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            String name = tool.name() == null ? "" : tool.name();
            String description = tool.description() == null ? "" : tool.description();
            String schemaJson = tool.inputSchema() == null ? "" : toJson(tool.inputSchema());
            McpSchema.ToolAnnotations annotations = tool.annotations();
            boolean readOnly = annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint());
            entries.add(new McpToolEntry(name, description, schemaJson, readOnly));
        }
        return entries;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "";
        }
    }
}
