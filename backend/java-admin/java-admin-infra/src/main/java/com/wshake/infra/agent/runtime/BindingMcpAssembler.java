package com.wshake.infra.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.McpEntry;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP 会话装配器：按合并后的 MCP 绑定集，把 Release 冻结连接配置装配为 agentscope MCP 客户端。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.3/§8.5：会话首启对绑定 MCP 实时握手，
 * 固定工具名单；握手失败/连接不可用按「未知即拒绝」拒绝首启（不降级放行）。
 *
 * <p>密钥来源：Binding 冻结的加密密钥（Agent/会话层补配）解密后注入请求头；
 * 明文只在本方法内存，不落库/日志。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class BindingMcpAssembler {

    /** 握手超时：对齐 docs「未知即拒绝」,连接不可用快速失败不拖住首启。 */
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);

    private final AgentMcpReleaseRepository releaseRepository;
    private final AgentSecretCipher secretCipher;
    private final ObjectMapper objectMapper;

    /**
     * 装配一个 MCP 客户端（连接 Release 冻结配置;wrapper 就绪后由调用方注册并握手）。
     *
     * @param entry 合并后的 MCP 绑定条目（releaseId + 冻结密钥）
     * @return MCP 客户端包装（未关闭;由调用方注册后持有）
     */
    public McpClientWrapper assembleOne(McpEntry entry) {
        AgentMcpRelease release = requireRelease(entry.mcpReleaseId());
        String name = release.getName();
        String transport = release.getTransport() == null ? "" : release.getTransport();
        String url = release.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("mcp release " + release.getId() + " 未配置 url");
        }
        // 静态头 + 绑定密钥 → Authorization 头（与 McpControlService.probe 同构）
        Map<String, String> headers = parseHeaders(release.getHeadersJson());
        String secret =
                entry.encryptedSecret() != null && !entry.encryptedSecret().isBlank()
                        ? secretCipher.decrypt(entry.encryptedSecret())
                        : secretCipher.decrypt(release.getEncryptedSecret());
        if (secret != null && !secret.isBlank()) {
            headers.putIfAbsent("Authorization", "Bearer " + secret);
        }

        Duration timeout = Duration.ofSeconds(10);
        McpClientBuilder builder =
                McpClientBuilder.create(name).timeout(timeout).initializationTimeout(timeout);
        switch (transport) {
            case "sse" -> builder.sseTransport(url);
            case "http" -> builder.streamableHttpTransport(url);
            default ->
                throw new IllegalStateException("mcp release " + release.getId() + " transport 不支持: " + transport);
        }
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }
        // 握手在 toolkit.registerMcpClient 时进行;这里仅构建 wrapper
        return builder.buildSync();
    }

    private AgentMcpRelease requireRelease(Long releaseId) {
        AgentMcpRelease release = releaseRepository.findById(releaseId);
        if (release == null || release.getIsEnabled() == null || release.getIsEnabled() != 1) {
            throw new IllegalStateException("mcp release " + releaseId + " 不可用(binding 引用缺失)");
        }
        return release;
    }

    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            objectMapper
                    .readValue(headersJson, new TypeReference<Map<String, Object>>() {})
                    .forEach((k, v) -> {
                        if (k != null && v != null) {
                            headers.put(k, String.valueOf(v));
                        }
                    });
            return headers;
        } catch (Exception e) {
            throw new IllegalStateException("mcp release headersJson 解析失败: " + e.getMessage());
        }
    }
}
