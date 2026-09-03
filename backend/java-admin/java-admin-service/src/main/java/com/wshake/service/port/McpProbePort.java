package com.wshake.service.port;

import java.util.List;
import java.util.Map;

/**
 * MCP 握手探测端口（service → infra 的 OkHttp HTTP/SSE 适配）。
 *
 * <p>业务层（McpControlService）只依赖本接口做连接验证与工具目录探测,不耦合 OkHttp/MCP SDK。
 * 握手失败/目录为空必须抛错（未知即拒绝）,由适配器转为 {@code McpProbeException}。
 *
 * @author wshake
 */
public interface McpProbePort {

    /**
     * 连接并拉取工具目录。
     *
     * @param command 探测命令（连接配置已在 service 层解密就绪,明文只在内存）
     * @return 工具条目列表（空列表按拒绝语义由调用方处理）
     */
    List<McpToolEntry> probe(ProbeCommand command);

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
}
