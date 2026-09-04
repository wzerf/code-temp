package com.wshake.infra.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.port.ModelProbePort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * 模型连接探测实现（{@link ModelProbePort} 的 OkHttp 适配）。
 *
 * <p>openai-compatible：{@code GET {baseUrl}/models} + Bearer。
 * anthropic：{@code GET {baseUrl}/models} + {@code x-api-key}。
 * 明文密钥只在本方法内存中注入请求头，不写日志。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class ModelProbeGateway implements ModelProbePort {

    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public ProbeResult probe(ProbeCommand command) {
        String url = modelsUrl(command.baseUrl());
        Request.Builder builder = new Request.Builder().url(url).get();
        String secret = command.plainSecret() == null ? "" : command.plainSecret();
        if (PROVIDER_ANTHROPIC.equals(command.provider())) {
            builder.header("x-api-key", secret);
            builder.header("anthropic-version", ANTHROPIC_VERSION);
        } else {
            builder.header("Authorization", "Bearer " + secret);
        }

        OkHttpClient client =
                okHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new BizException(ResultCode.PARAM_INVALID, "模型探测 HTTP " + response.code());
            }
            ResponseBody body = response.body();
            String json = body == null ? "" : body.string();
            return parseCatalog(json, command.modelName());
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException(ResultCode.PARAM_INVALID, "模型探测失败: " + e.getMessage());
        }
    }

    private ProbeResult parseCatalog(String json, String modelName) {
        if (json == null || json.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "模型探测失败: 空响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "模型探测失败: 响应不是 JSON");
        }
        List<String> ids = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            data = root.get("models");
        }
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null && id.isTextual()) {
                    ids.add(id.asText());
                }
            }
        }
        boolean catalogOnly = modelName == null || modelName.isBlank();
        boolean matched = catalogOnly ? !ids.isEmpty() : ids.contains(modelName);
        String message;
        if (catalogOnly) {
            message = "探测成功,共 " + ids.size() + " 条";
        } else if (matched) {
            message = "探测成功,目录含目标模型,共 " + ids.size() + " 条";
        } else {
            message = "探测成功但目录未含 modelName=" + modelName + ",共 " + ids.size() + " 条";
        }
        return new ProbeResult(ids, matched, message);
    }

    static String modelsUrl(String baseUrl) {
        String root = baseUrl == null ? "" : baseUrl.trim();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        if (root.endsWith("/models")) {
            return root;
        }
        return root + "/models";
    }
}
