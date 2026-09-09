package com.wshake.infra.videogen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.port.VideoGenerationPort;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
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

/**
 * xAI Imagine 视频 REST：{@code POST /v1/videos/generations} 取 request_id，再轮询 {@code GET /v1/videos/{id}}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XaiVideoAdapter implements VideoGenerationPort {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final VideoGenProperties properties;

    @Override
    public VideoGenResult generate(VideoGenCommand command) {
        String baseUrl = requireHttpsUrl(command.baseUrl(), "生视频地址必须为 https");
        String endpoint = joinUrl(baseUrl, "/v1/videos/generations");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", command.modelName());
        body.put("prompt", command.prompt());
        if (command.durationSeconds() != null) body.put("duration", command.durationSeconds());
        if (command.aspectRatio() != null && !command.aspectRatio().isBlank()) {
            body.put("aspect_ratio", command.aspectRatio().trim());
        }
        if (command.resolution() != null && !command.resolution().isBlank()) {
            body.put("resolution", command.resolution().trim());
        }
        if (command.imageUrl() != null && !command.imageUrl().isBlank()) {
            ObjectNode image = objectMapper.createObjectNode();
            image.put("url", requireHttpsUrl(command.imageUrl(), "参考图必须为 https"));
            body.set("image", image);
        }
        String requestId = startGeneration(endpoint, body, command.plainSecret());
        return pollUntilDone(baseUrl, requestId, command.plainSecret());
    }

    private String startGeneration(String endpoint, ObjectNode body, String plainSecret) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "生视频请求序列化失败");
        }
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + plainSecret)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();
        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(properties.getPollTimeout())
                .build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw mapHttpError(response.code(), respBody);
            }
            JsonNode root = objectMapper.readTree(respBody);
            String requestId = root.path("request_id").asText(null);
            if (requestId == null || requestId.isBlank()) {
                throw BizException.of(ResultCode.INTERNAL_ERROR, "生视频未返回 request_id");
            }
            return requestId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "生视频请求失败: " + e.getMessage());
        }
    }

    private VideoGenResult pollUntilDone(String baseUrl, String requestId, String plainSecret) {
        String endpoint = joinUrl(baseUrl, "/v1/videos/" + requestId);
        Duration timeout = properties.getPollTimeout() == null ? Duration.ofMinutes(8) : properties.getPollTimeout();
        Duration interval = properties.getPollInterval() == null ? Duration.ofSeconds(5) : properties.getPollInterval();
        long deadline = System.nanoTime() + timeout.toNanos();
        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(Math.max(15, interval.toSeconds() + 10), TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + plainSecret)
                .get()
                .build();
        while (true) {
            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw mapHttpError(response.code(), respBody);
                }
                JsonNode root = objectMapper.readTree(respBody);
                String status = root.path("status").asText("").trim().toLowerCase(Locale.ROOT);
                if ("done".equals(status)) {
                    String url = root.path("video").path("url").asText(null);
                    if (url == null || url.isBlank()) url = root.path("url").asText(null);
                    if (url == null || url.isBlank()) {
                        throw BizException.of(ResultCode.INTERNAL_ERROR, "生视频完成但缺少 video.url");
                    }
                    String safe = requireHttpsUrl(url, "生视频结果必须为 https");
                    Integer duration = null;
                    if (root.path("video").path("duration").isNumber()) {
                        duration = root.path("video").path("duration").asInt();
                    } else if (root.path("duration").isNumber()) {
                        duration = root.path("duration").asInt();
                    }
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("request_id", requestId);
                    raw.put("status", status);
                    return new VideoGenResult(safe, requestId, duration, raw);
                }
                if ("failed".equals(status) || "expired".equals(status)) {
                    String msg = root.path("error").path("message").asText(null);
                    if (msg == null || msg.isBlank()) msg = extractErrorMessage(respBody);
                    throw BizException.of(
                            ResultCode.INTERNAL_ERROR, "生视频" + ("expired".equals(status) ? "已过期" : "失败") + ": " + msg);
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "生视频轮询失败: " + e.getMessage());
            }
            if (System.nanoTime() >= deadline) {
                throw BizException.of(ResultCode.INTERNAL_ERROR, "生视频超时，请稍后重试");
            }
            sleep(interval);
        }
    }

    private static void sleep(Duration interval) {
        try {
            Thread.sleep(Math.max(200, interval.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.INTERNAL_ERROR, "生视频轮询被中断");
        }
    }

    private BizException mapHttpError(int code, String respBody) {
        String msg = extractErrorMessage(respBody);
        if (code == 429) {
            return BizException.of(ResultCode.PARAM_INVALID, "生视频繁忙，请稍后重试: " + msg);
        }
        if (code >= 400 && code < 500) {
            return BizException.of(ResultCode.PARAM_INVALID, "生视频参数错误: " + msg);
        }
        return BizException.of(ResultCode.INTERNAL_ERROR, "生视频服务暂不可用: " + msg);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode n = objectMapper.readTree(body);
            String m = n.path("error").path("message").asText(null);
            if (m != null && !m.isBlank()) return m;
            return body.length() > 500 ? body.substring(0, 500) : body;
        } catch (Exception e) {
            return body.length() > 500 ? body.substring(0, 500) : body;
        }
    }

    static String joinUrl(String base, String path) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (b.endsWith("/v1") && path.startsWith("/v1/")) {
            return b + path.substring(3);
        }
        return b + path;
    }

    private static String requireHttpsUrl(String raw, String message) {
        if (raw == null || raw.isBlank() || !raw.trim().startsWith("https://")) {
            throw BizException.of(ResultCode.PARAM_INVALID, message);
        }
        try {
            URI uri = new URI(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw BizException.of(ResultCode.PARAM_INVALID, message);
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
            throw BizException.of(ResultCode.PARAM_INVALID, message);
        }
        return raw.trim();
    }
}
