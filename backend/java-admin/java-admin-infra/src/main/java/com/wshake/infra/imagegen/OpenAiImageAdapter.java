package com.wshake.infra.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.port.ImageGenerationPort;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiImageAdapter implements ImageGenerationPort {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long CALL_TIMEOUT_SECONDS = 300;

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public ImageGenResult generate(ImageGenCommand command) {
        String baseUrl = requireHttpsUrl(command.baseUrl());
        String endpoint = joinUrl(baseUrl, "/v1/images/generations");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", command.modelName());
        body.put("prompt", command.prompt());
        if (command.n() != null) body.put("n", command.n());
        if (command.size() != null) body.put("size", command.size());
        if (command.quality() != null) body.put("quality", command.quality());
        if (command.style() != null) body.put("style", command.style());
        body.put("response_format", "b64_json");

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "生图请求序列化失败");
        }

        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + command.plainSecret())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String msg = extractErrorMessage(respBody);
                if (response.code() == 429) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "生图繁忙，请稍后重试: " + msg);
                }
                if (response.code() >= 400 && response.code() < 500) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "生图参数错误: " + msg);
                }
                throw BizException.of(ResultCode.INTERNAL_ERROR, "生图服务暂不可用: " + msg);
            }
            return parseImages(respBody);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "生图请求失败: " + e.getMessage());
        }
    }

    @Override
    public ProbeResult probe(ProbeCommand command) {
        String baseUrl = requireHttpsUrl(command.baseUrl());
        String endpoint = joinUrl(baseUrl, "/models");
        OkHttpClient client =
                okHttpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
        Request.Builder builder = new Request.Builder().url(endpoint).get();
        if ("anthropic".equalsIgnoreCase(command.provider())) {
            builder.header("x-api-key", command.plainSecret());
            builder.header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + command.plainSecret());
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return new ProbeResult(false, "探测失败 HTTP " + response.code() + " " + response.message());
            }
            if (command.modelName() != null && !command.modelName().isBlank()) {
                boolean matched = body.contains("\"" + command.modelName() + "\"");
                return new ProbeResult(true, matched ? "探测成功，已匹配模型" : "探测成功，但未匹配 modelName");
            }
            return new ProbeResult(true, "探测成功");
        } catch (Exception e) {
            return new ProbeResult(false, "探测异常: " + e.getMessage());
        }
    }

    private ImageGenResult parseImages(String respBody) throws Exception {
        JsonNode root = objectMapper.readTree(respBody);
        JsonNode data = root.get("data");
        List<ImageAsset> images = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : (ArrayNode) data) {
                String b64 = item.path("b64_json").asText(null);
                if (b64 != null && !b64.isBlank()) {
                    images.add(new ImageAsset(null, b64, "image/png", null, null));
                    continue;
                }
                String url = item.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    String safe = requireHttpsUrl(url);
                    rejectUnsafeHost(new URI(safe).getHost());
                    images.add(new ImageAsset(safe, null, null, null, null));
                }
            }
        }
        String revised = root.path("data").isArray() && root.path("data").size() > 0
                ? root.path("data").get(0).path("revised_prompt").asText(null)
                : root.path("revised_prompt").asText(null);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("raw", respBody);
        return new ImageGenResult(images, revised, raw);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode n = objectMapper.readTree(body);
            String m = n.path("error").path("message").asText(null);
            if (m != null) return m;
            return body.length() > 500 ? body.substring(0, 500) : body;
        } catch (Exception e) {
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
    }

    private static String joinUrl(String base, String path) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (b.endsWith("/v1") && path.startsWith("/v1/")) {
            return b + path.substring(3);
        }
        return b + path;
    }

    private static String requireHttpsUrl(String raw) {
        if (raw == null || raw.isBlank() || !raw.trim().startsWith("https://")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "生图地址必须为 https");
        }
        try {
            URI uri = new URI(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "仅支持 https 生图链接");
            }
            if (uri.getUserInfo() != null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "url 不得包含 user-info");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "url 缺少主机");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "仅支持 https 生图链接");
        }
        return raw.trim();
    }

    private static void rejectUnsafeHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "无法解析主机: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "地址指向内网/保留地址，已拒绝: " + host);
            }
        }
    }
}
