package com.wshake.infra.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.service.port.McpProbePort;
import com.wshake.service.port.McpProbePort.ProbeCommand;
import java.util.List;
import java.util.Map;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link McpProbeGateway} 基于官方 MCP Java SDK 的握手（MockWebServer 模拟 MCP 服务）。
 */
class McpProbeGatewayTest {

    private McpProbeGateway gateway;

    @BeforeEach
    void init() {
        gateway = new McpProbeGateway(new ObjectMapper());
    }

    /** 构造 JSON-RPC 成功响应;id 回显请求 id(SDK 使用 "uuid-seq" 字符串 id)。 */
    private static MockResponse okResult(String rpcId, String resultJson) {
        return new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"" + rpcId + "\",\"result\":" + resultJson + "}")
                .build();
    }

    /** 提取请求 JSON 中 "id":"..." 的字符串值。 */
    private static String rpcIdOf(String body) {
        int idx = body.indexOf("\"id\":");
        if (idx < 0) {
            return "0";
        }
        int start = idx + "\"id\":".length();
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        if (start < body.length() && body.charAt(start) == '"') {
            int end = body.indexOf('"', start + 1);
            return end > start ? body.substring(start + 1, end) : "0";
        }
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return end > start ? body.substring(start, end) : "0";
    }

    @Test
    void probe_parsesToolsFromJsonRpcServer() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String method = request.getMethod();
                    if (method == null || !"POST".equals(method)) {
                        // Firecrawl 等 Streamable HTTP：GET SSE 通知流返回 405 + text/plain
                        return new MockResponse.Builder()
                                .code(405)
                                .addHeader("Content-Type", "text/plain")
                                .body("Method Not Allowed")
                                .build();
                    }
                    String body = request.getBody().utf8();
                    String rpcId = rpcIdOf(body);
                    if (body.contains("\"method\":\"initialize\"")) {
                        return okResult(
                                rpcId,
                                "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"mock-mcp\",\"version\":\"1.0\"}}");
                    }
                    if (body.contains("\"method\":\"tools/list\"")) {
                        return okResult(
                                rpcId,
                                "{\"tools\":[{\"name\":\"firecrawl_search\",\"description\":\"Search the web\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}},\"annotations\":{\"readOnlyHint\":true}}]}");
                    }
                    // notifications/initialized 等通知:202 Accepted 空体
                    return new MockResponse.Builder().code(202).build();
                }
            });

            List<McpProbePort.McpToolEntry> tools = gateway.probe(new ProbeCommand(
                    "http", server.url("/mcp").toString(), Map.of("Authorization", "Bearer test"), 5000));

            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("firecrawl_search");
            assertThat(tools.get(0).readOnly()).isTrue();
            assertThat(tools.get(0).inputSchema()).contains("object");
        }
    }

    @Test
    void probe_httpError_throws() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    return new MockResponse.Builder().code(500).body("boom").build();
                }
            });

            assertThatThrownBy(() -> gateway.probe(
                            new ProbeCommand("http", server.url("/mcp").toString(), Map.of(), 5000)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
