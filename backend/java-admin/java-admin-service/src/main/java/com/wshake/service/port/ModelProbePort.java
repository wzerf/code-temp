package com.wshake.service.port;

import java.util.List;

/**
 * 模型连接探测端口（service → infra 的 OkHttp 适配）。
 *
 * <p>业务层只依赖本接口做最小探测（如 {@code GET /models}），不耦合 OkHttp。
 * 探测失败必须抛错（未知即拒绝），由适配器转为业务异常。
 *
 * @author wshake
 */
public interface ModelProbePort {

    /**
     * 探测远端模型目录是否可达，并尽量核对 {@code modelName} 是否在目录中。
     *
     * @param command 探测命令（连接配置已在 service 层解密就绪，明文只在内存）
     * @return 探测摘要
     */
    ProbeResult probe(ProbeCommand command);

    /**
     * 探测命令。
     *
     * @param provider   openai-compatible / anthropic
     * @param baseUrl    HTTPS 连接根（通常含版本路径，如 {@code https://api.openai.com/v1}）
     * @param modelName  期望的远端模型标识
     * @param plainSecret API Key 明文（仅内存）
     */
    record ProbeCommand(String provider, String baseUrl, String modelName, String plainSecret) {}

    /**
     * 探测摘要。
     *
     * @param remoteModelIds   远端目录中的模型 id
     * @param modelNameMatched {@code modelName} 是否出现在目录中；目录无法解析时为 false
     * @param message          人类可读摘要
     */
    record ProbeResult(List<String> remoteModelIds, boolean modelNameMatched, String message) {}
}
