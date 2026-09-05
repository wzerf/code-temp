package com.wshake.service.port;

import java.util.List;
import java.util.Map;

/**
 * MCP 握手探测端口（service → infra 的 HTTP/SSE 适配）。
 *
 * <p>业务层（McpControlService）只依赖本接口做连接验证与工具目录探测,不耦合 OkHttp/MCP SDK。
 * 握手失败必须抛错（未知即拒绝）。若对端以 HTTP 401 + {@code WWW-Authenticate} 声明
 * OAuth（RFC 9728）,则返回 {@link ProbeResult#oauth} 而不抛错,由 verify 呈现授权地址。
 *
 * @author wshake
 */
public interface McpProbePort {

    /**
     * 连接并拉取工具目录,或在需 OAuth 时返回授权端点。
     *
     * @param command 探测命令（连接配置已在 service 层解密就绪,明文只在内存）
     * @return 工具目录或 OAuth 挑战（空工具列表按拒绝语义由调用方处理）
     */
    ProbeResult probe(ProbeCommand command);

    /**
     * 探测命令。
     *
     * @param transport       sse / http
     * @param url             服务地址
     * @param headers         静态头（明文;来自 headers_json + 解密后的密钥注入）
     * @param connectTimeout  连接超时（毫秒）
     */
    record ProbeCommand(String transport, String url, Map<String, String> headers, int connectTimeout) {}

    /**
     * MCP 工具目录条目（工具名/描述/输入 schema/只读标记）。
     */
    record McpToolEntry(String name, String description, String inputSchema, boolean readOnly) {}

    /**
     * OAuth 挑战（授权端点已从 AS Metadata 解析）。
     *
     * @param authorizationEndpoint 授权端点（RFC 8414 {@code authorization_endpoint}）
     * @param scope                 空格分隔 scope
     * @param resource              受保护资源标识
     * @param resourceMetadataUrl   RFC 9728 Protected Resource Metadata URL
     */
    record OAuthChallenge(String authorizationEndpoint, String scope, String resource, String resourceMetadataUrl) {}

    /**
     * 探测结果：工具目录与 OAuth 挑战互斥。
     */
    record ProbeResult(List<McpToolEntry> tools, OAuthChallenge oauth) {
        public static ProbeResult tools(List<McpToolEntry> tools) {
            List<McpToolEntry> list = tools == null ? List.of() : List.copyOf(tools);
            return new ProbeResult(list, null);
        }

        public static ProbeResult oauth(OAuthChallenge oauth) {
            return new ProbeResult(List.of(), oauth);
        }

        public boolean oauthRequired() {
            return oauth != null
                    && oauth.authorizationEndpoint() != null
                    && !oauth.authorizationEndpoint().isBlank();
        }
    }
}
