package com.wshake.infra.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * 锁定 agentscope-core 2.0.1 与 MCP SDK 1.1.4 的运行面握手：
 * GET 405 + text/plain 不得导致 initialize 失败，且 {@code Tool.inputSchema()} 仍为 JsonSchema。
 */
class AgentscopeMcpHandshakeTest {

    @Test
    void registerMcpClient_get405PlainText_stillListsTools() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.setDispatcher(new Dispatcher() {
                @NotNull
                @Override
                public MockResponse dispatch(@NotNull RecordedRequest request) {
                    String method = request.getMethod();
                    if (method == null || !"POST".equals(method)) {
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
                                "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"mock-mcp\",\"version\":\"1.0\"}}");
                    }
                    if (body.contains("\"method\":\"tools/list\"")) {
                        return okResult(
                                rpcId,
                                "{\"tools\":[{\"name\":\"firecrawl_search\",\"description\":\"Search the web\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}},\"annotations\":{\"readOnlyHint\":true}}]}");
                    }
                    return new MockResponse.Builder().code(202).build();
                }
            });

            McpClientWrapper wrapper = McpClientBuilder.create("firecrawl")
                    .timeout(Duration.ofSeconds(5))
                    .initializationTimeout(Duration.ofSeconds(5))
                    .streamableHttpTransport(server.url("/mcp").toString())
                    .buildSync();
            try {
                Toolkit toolkit = new Toolkit();
                toolkit.registerMcpClient(wrapper).block(Duration.ofSeconds(10));
                assertThat(toolkit.getToolNames()).contains("firecrawl_search");
            } finally {
                wrapper.close();
            }
        }
    }

    private static MockResponse okResult(String rpcId, String resultJson) {
        return new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"" + rpcId + "\",\"result\":" + resultJson + "}")
                .build();
    }

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
}
