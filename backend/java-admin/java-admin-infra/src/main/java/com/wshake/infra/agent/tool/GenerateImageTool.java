package com.wshake.infra.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.infra.imagegen.ImageGenAdapterRegistry;
import com.wshake.infra.imagegen.ImageGenProperties;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.model.ModelControlService;
import com.wshake.service.port.ImageGenerationPort;
import com.wshake.service.port.StoragePort;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateImageTool implements AgentTool {

    private static final Set<String> ALLOWED_SIZES =
            Set.of("1024x1024", "1024x1792", "1792x1024", "512x512", "768x768");

    private final ModelControlService modelControlService;
    private final AgentSecretCipher secretCipher;
    private final ImageGenAdapterRegistry adapterRegistry;
    private final ImageGenProperties imageGenProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final StoragePort storagePort;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "generate_image";
    }

    @Override
    public String getDescription() {
        return "文生图：仅根据 prompt 从零生成。用于首次出图；当用户明确说\"基于上一张图改/去掉某个元素\"时，优先用 edit_image/multi_edit_image（需要 image_url），不要用本工具重画全图。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("prompt", Map.of("type", "string", "description", "生图提示词，1..4000 字符"));
        props.put(
                "size",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of("1024x1024", "1024x1792", "1792x1024"),
                        "description",
                        "图片尺寸"));
        props.put("n", Map.of("type", "integer", "minimum", 1, "maximum", 4, "description", "生成张数 1..4"));
        props.put("quality", Map.of("type", "string", "enum", List.of("standard", "hd")));
        props.put("style", Map.of("type", "string", "enum", List.of("vivid", "natural")));
        props.put(
                "aspect_ratio",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of("1:1", "16:9", "9:16", "4:3", "3:4", "auto"),
                        "description",
                        "xAI 生图宽高比"));
        props.put("resolution", Map.of("type", "string", "enum", List.of("1k", "2k"), "description", "xAI 分辨率"));
        props.put("model_release_id", Map.of("type", "integer", "description", "可选，指定生图模型 Release id"));
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
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String prompt = input.get("prompt") == null
                ? ""
                : String.valueOf(input.get("prompt")).trim();
        if (prompt.isEmpty()) {
            return Mono.just(ToolResultBlock.error("prompt 不能为空"));
        }
        if (prompt.length() > imageGenProperties.getMaxPromptChars()) {
            return Mono.just(ToolResultBlock.error("prompt 过长，上限 " + imageGenProperties.getMaxPromptChars()));
        }
        String size = input.get("size") == null
                ? "1024x1024"
                : String.valueOf(input.get("size")).trim();
        if (!ALLOWED_SIZES.contains(size)) {
            return Mono.just(ToolResultBlock.error("size 不合法，允许: " + ALLOWED_SIZES));
        }
        int n = 1;
        if (input.get("n") != null) {
            try {
                n = Integer.parseInt(String.valueOf(input.get("n")));
            } catch (NumberFormatException e) {
                return Mono.just(ToolResultBlock.error("n 必须为整数 1..4"));
            }
        }
        if (n < 1 || n > imageGenProperties.getMaxImagesPerCall()) {
            return Mono.just(ToolResultBlock.error("n 必须在 1.." + imageGenProperties.getMaxImagesPerCall()));
        }
        String quality = input.get("quality") == null
                ? null
                : String.valueOf(input.get("quality")).trim();
        String style = input.get("style") == null
                ? null
                : String.valueOf(input.get("style")).trim();
        Long requestedReleaseId = null;
        if (input.get("model_release_id") != null) {
            try {
                requestedReleaseId = Long.parseLong(String.valueOf(input.get("model_release_id")));
            } catch (NumberFormatException e) {
                return Mono.just(ToolResultBlock.error("model_release_id 必须为整数"));
            }
        }
        String aspectRatio = input.get("aspect_ratio") == null
                ? null
                : String.valueOf(input.get("aspect_ratio")).trim();
        if (aspectRatio != null && aspectRatio.isBlank()) aspectRatio = null;
        String resolution = input.get("resolution") == null
                ? null
                : String.valueOf(input.get("resolution")).trim();
        if (resolution != null && resolution.isBlank()) resolution = null;
        final Long capturedUserId = currentUserId();
        final String finalPrompt = prompt;
        final String finalSize = size;
        final int finalN = n;
        final String finalQuality = quality == null || quality.isBlank() ? null : quality;
        final String finalStyle = style == null || style.isBlank() ? null : style;
        final Long finalReleaseId = requestedReleaseId;
        final String finalAspectRatio = aspectRatio;
        final String finalResolution = resolution;

        return Mono.fromCallable(() -> doGenerate(
                        finalPrompt,
                        finalSize,
                        finalN,
                        finalQuality,
                        finalStyle,
                        finalAspectRatio,
                        finalResolution,
                        finalReleaseId,
                        capturedUserId))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    log.warn("generate_image failed: {}", msg);
                    if (e instanceof BizException be) {
                        return Mono.just(ToolResultBlock.error(be.getMessage()));
                    }
                    return Mono.just(ToolResultBlock.error("生图失败: " + msg));
                });
    }

    private ToolResultBlock doGenerate(
            String prompt,
            String size,
            int n,
            String quality,
            String style,
            String aspectRatio,
            String resolution,
            Long requestedReleaseId,
            Long capturedUserId)
            throws Exception {
        Long userId = capturedUserId;
        checkRateLimit(userId);

        AgentModelRelease release = resolveRelease(requestedReleaseId, userId);
        if (release == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "暂未配置生图模型，请在模型管理新建 code=image 的模型");
        }
        enforceGuardrails(release, size, n);
        String plainSecret = secretCipher.decrypt(release.getEncryptedSecret());
        if (plainSecret == null || plainSecret.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "生图模型未配置密钥");
        }
        ImageGenerationPort port = adapterRegistry.forRelease(release);
        ImageGenerationPort.ImageGenResult result = port.generate(new ImageGenerationPort.ImageGenCommand(
                release.getId(),
                release.getProvider(),
                release.getBaseUrl(),
                release.getModelName(),
                plainSecret,
                prompt,
                size,
                n,
                quality,
                style,
                aspectRatio,
                resolution,
                "b64_json",
                null));

        List<Object> blocks = new ArrayList<>();
        for (ImageGenerationPort.ImageAsset asset : result.images()) {
            ImageBlock imageBlock = toImageBlock(asset);
            if (imageGenProperties.isUploadToStorage()) {
                try {
                    String b64 = extractB64(imageBlock);
                    if (b64 != null) {
                        byte[] bytes = Base64.getDecoder().decode(b64);
                        String key = "agent/image/" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000)
                                + ".png";
                        String mimeType = "image/png";
                        storagePort.put(new StoragePort.PutCommand(
                                key, new java.io.ByteArrayInputStream(bytes), bytes.length, mimeType));
                        String url = storagePort.url(key).orElse(null);
                        if (url == null) url = storagePort.presignGet(key, java.time.Duration.ofHours(1));
                        log.info("generate_image uploaded to storage key={} url={}", key, url);
                    }
                } catch (Exception e) {
                    log.warn("generate_image upload to storage failed: {}", e.getMessage());
                }
            }
            blocks.add(imageBlock);
        }
        if (blocks.isEmpty()) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "生图返回为空");
        }
        String text = result.revisedPrompt() != null && !result.revisedPrompt().isBlank()
                ? "revised_prompt: " + result.revisedPrompt() + " | model: " + release.getModelName()
                : "model: " + release.getModelName() + " size=" + size + " n=" + n;
        blocks.add(TextBlock.builder().text(text).build());
        @SuppressWarnings("unchecked")
        List blocksTyped = blocks;
        return ToolResultBlock.of(blocksTyped);
    }

    private void checkRateLimit(Long userId) {
        if (userId == null || userId <= 0) return;
        String key = "image:gen:" + userId;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, imageGenProperties.getRateWindow());
            }
            if (count != null && count > imageGenProperties.getRateBurst()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "生图繁忙，请稍后重试");
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

    private AgentModelRelease resolveRelease(Long requestedReleaseId, Long userId) {
        if (requestedReleaseId != null) {
            return modelControlService.requireUsableRelease(requestedReleaseId, userId);
        }
        var pool = modelControlService.listAvailableFiltered(userId, "image");
        if (pool == null || pool.isEmpty()) return null;
        Long id = pool.getFirst().id();
        return modelControlService.requireUsableRelease(id, userId);
    }

    private void enforceGuardrails(AgentModelRelease release, String size, int n) {
        String pg = release.getParameterGuardrails();
        if (pg == null || pg.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(pg);
            JsonNode sizeNode = root.path("size");
            if (sizeNode.isArray() && !sizeNode.isEmpty()) {
                boolean allowed = false;
                for (JsonNode v : sizeNode) {
                    if (size.equals(v.asText())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "size 不在模型护栏允许范围");
                }
            }
            JsonNode nNode = root.path("n");
            if (nNode.isObject()) {
                int min = nNode.path("min").asInt(1);
                int max = nNode.path("max").asInt(4);
                if (n < min || n > max) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "n 超出护栏范围 " + min + ".." + max);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.debug("guardrails parse failed: {}", e.getMessage());
        }
    }

    private ImageBlock toImageBlock(ImageGenerationPort.ImageAsset asset) throws Exception {
        if (asset.b64Json() != null && !asset.b64Json().isBlank()) {
            String mime = asset.mimeType() != null && !asset.mimeType().isBlank() ? asset.mimeType() : "image/png";
            return ImageBlock.builder()
                    .source(new Base64Source(mime, asset.b64Json().trim()))
                    .build();
        }
        if (asset.url() != null && !asset.url().isBlank()) {
            String url = asset.url().trim();
            if (!url.startsWith("https://")) {
                throw BizException.of(ResultCode.PARAM_INVALID, "生图返回的 url 必须为 https");
            }
            byte[] bytes = downloadImage(url);
            String mime = asset.mimeType() != null ? asset.mimeType() : guessMime(url, null);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            return ImageBlock.builder().source(new Base64Source(mime, b64)).build();
        }
        throw BizException.of(ResultCode.INTERNAL_ERROR, "生图结果缺少 url/b64_json");
    }

    private byte[] downloadImage(String url) throws Exception {
        URI uri = new URI(url);
        rejectUnsafeHost(uri.getHost());
        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .header("User-Agent", "wshake-agent/1.0")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("图片下载失败 HTTP " + response.code());
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
            if (contentType != null && !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Content-Type 不是图片: " + contentType);
            }
            ResponseBody body = response.body();
            if (body == null) throw new IllegalArgumentException("空响应体");
            byte[] bytes = body.bytes();
            if (bytes.length > imageGenProperties.getMaxImageBytes()) {
                throw new IllegalArgumentException("图片过大 " + bytes.length);
            }
            if (bytes.length == 0) throw new IllegalArgumentException("图片为空");
            return bytes;
        }
    }

    private static String extractB64(ImageBlock block) {
        try {
            Object src = block.getSource();
            if (src != null) {
                try {
                    var m = src.getClass().getMethod("getData");
                    Object v = m.invoke(src);
                    if (v instanceof String s) return s;
                } catch (Exception e) {
                    log.debug("extractB64 getData failed: {}", e.toString());
                }
                try {
                    var m = src.getClass().getMethod("data");
                    Object v = m.invoke(src);
                    if (v instanceof String s) return s;
                } catch (Exception e) {
                    log.debug("extractB64 data failed: {}", e.toString());
                }
            }
        } catch (Exception e) {
            log.debug("extractB64 failed: {}", e.toString());
        }
        return null;
    }

    private static String guessMime(String url, String contentType) {
        if (contentType != null && !contentType.isBlank()) return contentType;
        String lower = Ascii.toLowerCase(url);
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif")) return "image/gif";
        return "image/jpeg";
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
