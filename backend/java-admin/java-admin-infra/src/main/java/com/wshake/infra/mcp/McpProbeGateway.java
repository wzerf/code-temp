package com.wshake.infra.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.service.port.McpProbePort;
import com.wshake.service.port.McpProbePort.McpToolEntry;
import com.wshake.service.port.McpProbePort.OAuthChallenge;
import com.wshake.service.port.McpProbePort.ProbeCommand;
import com.wshake.service.port.McpProbePort.ProbeResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * MCP 握手探测实现（service {@link McpProbePort} 的官方 Java SDK 适配）。
 *
 * <p>基于 {@code io.modelcontextprotocol.sdk} 1.1.x 的 Streamable HTTP 客户端
 * （与 agentscope-core 2.0.1 的 {@code JsonSchema inputSchema()} 二进制兼容，不能用 2.x）:
 * SDK 负责 JSON-RPC {@code initialize} + {@code tools/list}、text/event-stream 帧解析,
 * 并兼容 202+text/plain 空响应与 GET 405（Firecrawl 等不提供 SSE 通知流）。
 * 握手失败/目录为空由调用方按「未知即拒绝」处理。
 *
 * <p>若 SDK 握手失败且对端返回 HTTP 401 + {@code WWW-Authenticate} {@code resource_metadata},
 * 则按 RFC 9728 / RFC 8414 发现 {@code authorization_endpoint},供 verify 弹窗呈现。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class McpProbeGateway implements McpProbePort {

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String INITIALIZE_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":"
                    + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                    + "\"clientInfo\":{\"name\":\"java-admin\",\"version\":\"1.0\"}}}";
    private static final Pattern AUTH_PARAM = Pattern.compile("(?i)\\b([a-z0-9_-]+)\\s*=\\s*(\"[^\"]*\"|[^\\s,]+)");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public ProbeResult probe(ProbeCommand command) {
        try {
            return ProbeResult.tools(listToolsViaSdk(command));
        } catch (RuntimeException e) {
            OAuthChallenge oauth = discoverOAuth(command);
            if (oauth != null) {
                return ProbeResult.oauth(oauth);
            }
            throw e;
        }
    }

    private List<McpToolEntry> listToolsViaSdk(ProbeCommand command) {
        Map<String, String> headers = command.headers();
        int timeoutMs = timeoutMs(command);

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
                .clientInfo(new McpSchema.Implementation("java-admin", "1.0"))
                .requestTimeout(Duration.ofMillis(timeoutMs))
                .build()) {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpSchema.Tool> tools = result.tools() == null ? List.of() : result.tools();
            return mapTools(tools);
        }
    }

    /**
     * 握手失败后再用 OkHttp 读 401 头并发现授权端点。用 POST initialize,避免 GET 订阅 SSE 挂起。
     */
    private OAuthChallenge discoverOAuth(ProbeCommand command) {
        if (command.url() == null || command.url().isBlank() || !isHttpUrl(command.url())) {
            return null;
        }
        OkHttpClient http = timedClient(command);
        String wwwAuthenticate = readWwwAuthenticate(http, command);
        if (wwwAuthenticate == null || wwwAuthenticate.isBlank()) {
            return null;
        }
        String metadataUrl = authParam(wwwAuthenticate, "resource_metadata");
        if (!isHttpUrl(metadataUrl)) {
            return null;
        }
        metadataUrl = URI.create(command.url()).resolve(metadataUrl).toString();
        JsonNode prm = getJson(http, metadataUrl);
        if (prm == null) {
            return null;
        }
        String resource = text(prm, "resource");
        String issuer = firstAuthorizationServer(prm);
        if (!isHttpUrl(issuer)) {
            issuer = isHttpUrl(resource) ? resource : command.url();
        }
        JsonNode as = getJson(http, authorizationServerMetadataUrl(issuer));
        if (as == null) {
            as = getJson(http, trimTrailingSlash(issuer) + "/.well-known/oauth-authorization-server");
        }
        if (as == null) {
            return null;
        }
        String authorizationEndpoint = text(as, "authorization_endpoint");
        if (!isHttpUrl(authorizationEndpoint)) {
            return null;
        }
        String scope = authParam(wwwAuthenticate, "scope");
        if (scope == null || scope.isBlank()) {
            scope = joinScopes(prm.get("scopes_supported"));
        }
        return new OAuthChallenge(authorizationEndpoint, nullToEmpty(scope), nullToEmpty(resource), metadataUrl);
    }

    private String readWwwAuthenticate(OkHttpClient http, ProbeCommand command) {
        String header = exchangeWwwAuthenticate(http, command, true);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return exchangeWwwAuthenticate(http, command, false);
    }

    private String exchangeWwwAuthenticate(OkHttpClient http, ProbeCommand command, boolean post) {
        Request.Builder builder = new Request.Builder().url(command.url());
        builder.header("Accept", "application/json, text/event-stream");
        if (post) {
            builder.header("Content-Type", "application/json");
            builder.header("MCP-Protocol-Version", "2025-06-18");
            builder.post(RequestBody.create(INITIALIZE_BODY, JSON));
        }
        applyHeaders(builder, command.headers());
        try (Response response = http.newCall(builder.build()).execute()) {
            if (response.code() != HTTP_UNAUTHORIZED) {
                return null;
            }
            return response.header("WWW-Authenticate");
        } catch (IOException e) {
            return null;
        }
    }

    private JsonNode getJson(OkHttpClient http, String url) {
        if (!isHttpUrl(url)) {
            return null;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            return objectMapper.readTree(body.string());
        } catch (Exception e) {
            return null;
        }
    }

    private OkHttpClient timedClient(ProbeCommand command) {
        int timeoutMs = timeoutMs(command);
        return okHttpClient
                .newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    private static void applyHeaders(Request.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach(builder::header);
    }

    static String authorizationServerMetadataUrl(String issuer) {
        URI uri = URI.create(issuer);
        String path = uri.getRawPath();
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return origin + "/.well-known/oauth-authorization-server";
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return origin + "/.well-known/oauth-authorization-server" + path;
    }

    static String authParam(String wwwAuthenticate, String name) {
        if (wwwAuthenticate == null || name == null) {
            return null;
        }
        Matcher matcher = AUTH_PARAM.matcher(wwwAuthenticate);
        while (matcher.find()) {
            if (!name.equalsIgnoreCase(matcher.group(1))) {
                continue;
            }
            String value = matcher.group(2);
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
        return null;
    }

    private static String firstAuthorizationServer(JsonNode prm) {
        JsonNode servers = prm.get("authorization_servers");
        if (servers == null || !servers.isArray() || servers.isEmpty()) {
            return "";
        }
        return servers.get(0).asText("");
    }

    private static String joinScopes(JsonNode scopes) {
        if (scopes == null || !scopes.isArray() || scopes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : scopes) {
            String s = n.asText("");
            if (s.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int timeoutMs(ProbeCommand command) {
        return command.connectTimeout() > 0 ? command.connectTimeout() : 5000;
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
