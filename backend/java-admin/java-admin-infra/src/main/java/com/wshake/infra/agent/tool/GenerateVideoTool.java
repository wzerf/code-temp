package com.wshake.infra.agent.tool;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.infra.agent.runtime.AgentContext;
import com.wshake.infra.videogen.VideoGenAdapterRegistry;
import com.wshake.infra.videogen.VideoGenProperties;
import com.wshake.service.port.StoragePort;
import com.wshake.service.port.VideoGenerationPort;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateVideoTool implements AgentTool {

    private static final Set<String> ALLOWED_ASPECT = Set.of("1:1", "16:9", "9:16", "4:3", "3:4", "auto");
    private static final Set<String> ALLOWED_RESOLUTION = Set.of("480p", "720p", "1080p");

    private final VideoGenAdapterRegistry adapterRegistry;
    private final VideoGenProperties videoGenProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final StoragePort storagePort;
    private final OkHttpClient okHttpClient;

    @Override
    public String getName() {
        return "generate_video";
    }

    @Override
    public String getDescription() {
        return "文生视频：根据 prompt 生成短视频。可选 image_url 将参考图作为首帧做图生视频。视频生成较慢，一次调用即可等待结果，不要连续重复调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("prompt", Map.of("type", "string", "description", "生视频提示词，1..4000 字符"));
        props.put("duration", Map.of("type", "integer", "minimum", 1, "maximum", 15, "description", "时长秒数，默认 5，最大 15"));
        props.put(
                "aspect_ratio",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of("1:1", "16:9", "9:16", "4:3", "3:4", "auto"),
                        "description",
                        "宽高比"));
        props.put(
                "resolution", Map.of("type", "string", "enum", List.of("480p", "720p", "1080p"), "description", "分辨率"));
        props.put("image_url", Map.of("type", "string", "description", "可选参考图 https URL，作为图生视频首帧"));
        schema.put("properties", props);
        schema.put("required", List.of("prompt"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var capturedVideoModel = AgentContext.videoModelOrNull(param);
        if (capturedVideoModel == null) capturedVideoModel = AgentContext.videoModelOrNull();
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String prompt = input.get("prompt") == null
                ? ""
                : String.valueOf(input.get("prompt")).trim();
        if (prompt.isEmpty()) {
            return Mono.just(ToolResultBlock.error("prompt 不能为空"));
        }
        if (prompt.length() > videoGenProperties.getMaxPromptChars()) {
            return Mono.just(ToolResultBlock.error("prompt 过长，上限 " + videoGenProperties.getMaxPromptChars()));
        }
        int duration = videoGenProperties.getDefaultDurationSeconds();
        if (input.get("duration") != null) {
            try {
                duration = Integer.parseInt(String.valueOf(input.get("duration")));
            } catch (NumberFormatException e) {
                return Mono.just(ToolResultBlock.error("duration 必须为整数秒"));
            }
        }
        if (duration < videoGenProperties.getMinDurationSeconds()
                || duration > videoGenProperties.getMaxDurationSeconds()) {
            return Mono.just(ToolResultBlock.error("duration 必须在 "
                    + videoGenProperties.getMinDurationSeconds()
                    + ".."
                    + videoGenProperties.getMaxDurationSeconds()));
        }
        String aspectRatio = blankToNull(input.get("aspect_ratio"));
        if (aspectRatio != null && !ALLOWED_ASPECT.contains(aspectRatio)) {
            return Mono.just(ToolResultBlock.error("aspect_ratio 不合法"));
        }
        String resolution = blankToNull(input.get("resolution"));
        if (resolution != null && !ALLOWED_RESOLUTION.contains(resolution)) {
            return Mono.just(ToolResultBlock.error("resolution 不合法"));
        }
        String imageUrl = blankToNull(input.get("image_url"));
        if (imageUrl != null && !imageUrl.startsWith("https://")) {
            return Mono.just(ToolResultBlock.error("image_url 必须为 https"));
        }
        final Long capturedUserId = currentUserId();
        final String finalPrompt = prompt;
        final int finalDuration = duration;
        final String finalAspect = aspectRatio;
        final String finalResolution = resolution;
        final String finalImageUrl = imageUrl;
        final var finalVideoModel = capturedVideoModel;
        return Mono.fromCallable(() -> doGenerate(
                        finalPrompt,
                        finalDuration,
                        finalAspect,
                        finalResolution,
                        finalImageUrl,
                        capturedUserId,
                        finalVideoModel))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    log.warn("generate_video failed: {}", msg);
                    if (e instanceof BizException be) {
                        return Mono.just(ToolResultBlock.error(be.getMessage()));
                    }
                    return Mono.just(ToolResultBlock.error("生视频失败: " + msg));
                });
    }

    private ToolResultBlock doGenerate(
            String prompt,
            int duration,
            String aspectRatio,
            String resolution,
            String imageUrl,
            Long capturedUserId,
            com.wshake.infra.agent.runtime.AgentRunPlan.VideoModelConfig videoModelParam)
            throws Exception {
        checkRateLimit(capturedUserId);
        var videoModel = videoModelParam;
        if (videoModel == null || !videoModel.isConfigured()) {
            videoModel = AgentContext.videoModelOrNull();
        }
        if (videoModel == null || !videoModel.isConfigured()) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "当前 Agent 未配置生视频模型，请在 Agent 管理中编辑草稿→勾选生视频并配置 BaseUrl/ModelName/密钥→发布；已有会话需新建会话或「固定 Revision」后重试");
        }
        VideoGenerationPort port = adapterRegistry.forVideoModel(videoModel);
        VideoGenerationPort.VideoGenResult result = port.generate(new VideoGenerationPort.VideoGenCommand(
                videoModel.provider(),
                videoModel.baseUrl(),
                videoModel.modelName(),
                videoModel.plainSecret(),
                prompt,
                duration,
                aspectRatio,
                resolution,
                imageUrl));
        String url = result.url();
        if (url == null || url.isBlank()) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "生视频返回为空");
        }
        if (videoGenProperties.isUploadToStorage()) {
            try {
                byte[] bytes = downloadVideo(url);
                String key = "agent/video/" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000) + ".mp4";
                storagePort.put(new StoragePort.PutCommand(
                        key, new java.io.ByteArrayInputStream(bytes), bytes.length, "video/mp4"));
                String stored = storagePort.url(key).orElse(null);
                if (stored == null) stored = storagePort.presignGet(key, java.time.Duration.ofHours(1));
                if (stored != null && !stored.isBlank()) {
                    log.info("generate_video uploaded to storage key={} url={}", key, stored);
                    url = stored;
                }
            } catch (Exception e) {
                log.warn("generate_video upload to storage failed: {}", e.getMessage());
            }
        }
        int shownDuration = result.durationSeconds() != null ? result.durationSeconds() : duration;
        String text = "video_url: " + url + " | model: " + videoModel.modelName() + " duration=" + shownDuration;
        return ToolResultBlock.of(List.of(TextBlock.builder().text(text).build()));
    }

    private void checkRateLimit(Long userId) {
        if (userId == null || userId <= 0) return;
        String key = "video:gen:" + userId;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, videoGenProperties.getRateWindow());
            }
            if (count != null && count > videoGenProperties.getRateBurst()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "生视频繁忙，请稍后重试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.debug("rate limit check failed, allow: {}", e.getMessage());
        }
    }

    private Long currentUserId() {
        try {
            return com.wshake.common.request.RequestContext.userIdOrNull();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] downloadVideo(String url) throws Exception {
        URI uri = new URI(url);
        rejectUnsafeHost(uri.getHost());
        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "video/*,*/*")
                .header("User-Agent", "wshake-agent/1.0")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("视频下载失败 HTTP " + response.code());
            }
            String finalHost = response.request().url().host();
            if (!finalHost.equalsIgnoreCase(uri.getHost())) {
                rejectUnsafeHost(finalHost);
            }
            String contentType = response.header("Content-Type");
            if (contentType != null) {
                contentType = Ascii.toLowerCase(
                        Splitter.on(';').splitToList(contentType).getFirst().trim());
            }
            if (contentType != null
                    && !contentType.startsWith("video/")
                    && !contentType.equals("application/octet-stream")) {
                throw new IllegalArgumentException("Content-Type 不是视频: " + contentType);
            }
            ResponseBody body = response.body();
            if (body == null) throw new IllegalArgumentException("空响应体");
            byte[] bytes = body.bytes();
            if (bytes.length > videoGenProperties.getMaxVideoBytes()) {
                throw new IllegalArgumentException("视频过大 " + bytes.length);
            }
            if (bytes.length == 0) throw new IllegalArgumentException("视频为空");
            return bytes;
        }
    }

    private static String blankToNull(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
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
