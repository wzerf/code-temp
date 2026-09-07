package com.wshake.infra.agent.runtime;

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
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class EditImageTool implements AgentTool {

    private final ModelControlService modelControlService;
    private final AgentSecretCipher secretCipher;
    private final ImageGenAdapterRegistry adapterRegistry;
    private final ImageGenProperties imageGenProperties;
    private final StoragePort storagePort;

    @Override
    public String getName() {
        return "edit_image";
    }

    @Override
    public String getDescription() {
        return "单图编辑：基于一张参考图 image_url 按 prompt 改图，尽量保持原图其余内容不变。用于\"改这张图/去掉XX/换成XX\"；优先传入上一张生成图的 image_url；若只有 base64，可先用 view_image 或直接传 data:image URL。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("prompt", Map.of("type", "string", "description", "编辑指令，1..4000 字符"));
        props.put("image_url", Map.of("type", "string", "description", "参考图 https URL"));
        props.put(
                "aspect_ratio", Map.of("type", "string", "enum", List.of("1:1", "16:9", "9:16", "4:3", "3:4", "auto")));
        props.put("resolution", Map.of("type", "string", "enum", List.of("1k", "2k")));
        props.put("model_release_id", Map.of("type", "integer", "description", "可选，生图模型 Release id"));
        schema.put("properties", props);
        schema.put("required", List.of("prompt", "image_url"));
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
        String imageUrl = input.get("image_url") == null
                ? ""
                : String.valueOf(input.get("image_url")).trim();
        if (prompt.isEmpty()) return Mono.just(ToolResultBlock.error("prompt 不能为空"));
        if (prompt.length() > imageGenProperties.getMaxPromptChars()) {
            return Mono.just(ToolResultBlock.error("prompt 过长，上限 " + imageGenProperties.getMaxPromptChars()));
        }
        if (imageUrl.isEmpty()) return Mono.just(ToolResultBlock.error("image_url 不能为空"));
        if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("data:image/")) {
            return Mono.just(ToolResultBlock.error("image_url 必须为 https 或 data:image base64"));
        }
        String aspectRatio = input.get("aspect_ratio") == null
                ? null
                : String.valueOf(input.get("aspect_ratio")).trim();
        if (aspectRatio != null && aspectRatio.isBlank()) aspectRatio = null;
        String resolution = input.get("resolution") == null
                ? null
                : String.valueOf(input.get("resolution")).trim();
        if (resolution != null && resolution.isBlank()) resolution = null;
        Long releaseId = null;
        if (input.get("model_release_id") != null) {
            try {
                releaseId = Long.parseLong(String.valueOf(input.get("model_release_id")));
            } catch (NumberFormatException e) {
                return Mono.just(ToolResultBlock.error("model_release_id 必须为整数"));
            }
        }
        final Long capturedUserId = com.wshake.common.request.RequestContext.userIdOrNull();
        final String fp = prompt;
        final String fu = imageUrl;
        final String fa = aspectRatio;
        final String fr = resolution;
        final Long frId = releaseId;
        return Mono.fromCallable(() -> doEdit(fp, fu, fa, fr, frId, capturedUserId))
                .onErrorResume(e -> {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    log.warn("edit_image failed: {}", msg);
                    if (e instanceof BizException be) return Mono.just(ToolResultBlock.error(be.getMessage()));
                    return Mono.just(ToolResultBlock.error("改图失败: " + msg));
                });
    }

    private ToolResultBlock doEdit(
            String prompt,
            String imageUrl,
            String aspectRatio,
            String resolution,
            Long requestedReleaseId,
            Long capturedUserId)
            throws Exception {
        Long userId = capturedUserId;
        AgentModelRelease release = resolveRelease(requestedReleaseId, userId);
        if (release == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "暂未配置生图模型，请在模型管理新建 code=image 的模型");
        }
        String plainSecret = secretCipher.decrypt(release.getEncryptedSecret());
        if (plainSecret == null || plainSecret.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "生图模型未配置密钥");
        }
        String resolvedUrl = ensurePublicImageUrl(imageUrl);
        ImageGenerationPort port = adapterRegistry.forRelease(release);
        ImageGenerationPort.ImageGenResult result = port.generate(new ImageGenerationPort.ImageGenCommand(
                release.getId(),
                release.getProvider(),
                release.getBaseUrl(),
                release.getModelName(),
                plainSecret,
                prompt,
                null,
                1,
                null,
                null,
                aspectRatio,
                resolution,
                "b64_json",
                List.of(resolvedUrl)));
        List<Object> blocks = new ArrayList<>();
        for (ImageGenerationPort.ImageAsset asset : result.images()) {
            blocks.add(toImageBlock(asset));
        }
        if (blocks.isEmpty()) throw BizException.of(ResultCode.INTERNAL_ERROR, "改图返回为空");
        String text = result.revisedPrompt() != null && !result.revisedPrompt().isBlank()
                ? "revised_prompt: " + result.revisedPrompt() + " | model: " + release.getModelName()
                : "model: " + release.getModelName();
        blocks.add(TextBlock.builder().text(text).build());
        @SuppressWarnings("unchecked")
        List blocksTyped = blocks;
        return ToolResultBlock.of(blocksTyped);
    }

    private AgentModelRelease resolveRelease(Long requestedReleaseId, Long userId) {
        if (requestedReleaseId != null) return modelControlService.requireUsableRelease(requestedReleaseId, userId);
        var pool = modelControlService.listAvailableFiltered(userId, "image");
        if (pool == null || pool.isEmpty()) return null;
        return modelControlService.requireUsableRelease(pool.getFirst().id(), userId);
    }

    private String ensurePublicImageUrl(String imageUrl) {
        if (imageUrl.startsWith("https://")) {
            if (isStorageHost(imageUrl)) return imageUrl;
            try {
                byte[] bytes = downloadToStorage(imageUrl, null);
                return uploadBytes(bytes, "image/png");
            } catch (Exception e) {
                log.warn("edit_image: 参考图转存失败，回退直传原 URL: {}", e.getMessage());
                return imageUrl;
            }
        }
        if (imageUrl.startsWith("data:image/")) {
            int comma = imageUrl.indexOf(',');
            if (comma < 0) throw BizException.of(ResultCode.PARAM_INVALID, "image_url data URI 非法");
            String b64 = imageUrl.substring(comma + 1).trim();
            String mime = "image/png";
            int semi = imageUrl.indexOf(';', 5);
            if (semi > 5 && semi < comma) mime = imageUrl.substring(5, semi);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(b64);
            } catch (Exception e) {
                throw BizException.of(ResultCode.PARAM_INVALID, "image_url base64 解码失败");
            }
            return uploadBytes(bytes, mime);
        }
        throw BizException.of(ResultCode.PARAM_INVALID, "image_url 必须为 https 或 data:image");
    }

    private boolean isStorageHost(String url) {
        try {
            String lower = url.toLowerCase();
            return lower.contains("minio") || lower.contains("s3") || lower.contains("storage");
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] downloadToStorage(String url, String fallbackMime) throws Exception {
        java.net.URI uri = new java.net.URI(url);
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("image_url 缺少主机");
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(15))
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "image/*")
                .get()
                .build();
        try (okhttp3.Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IllegalArgumentException("下载参考图失败 HTTP " + resp.code());
            okhttp3.ResponseBody body = resp.body();
            if (body == null) throw new IllegalArgumentException("参考图空响应");
            return body.bytes();
        }
    }

    private String uploadBytes(byte[] bytes, String mime) {
        String ext = mime.contains("png") ? ".png" : mime.contains("jpeg") || mime.contains("jpg") ? ".jpg" : ".png";
        String key = "agent/edit-ref/" + UUID.randomUUID() + ext;
        storagePort.put(new StoragePort.PutCommand(key, new ByteArrayInputStream(bytes), bytes.length, mime));
        String url = storagePort.url(key).orElse(null);
        if (url == null) url = storagePort.presignGet(key, Duration.ofHours(2));
        log.info("edit_image: 参考图已转存 key={} url={}", key, url);
        return url;
    }

    private ImageBlock toImageBlock(ImageGenerationPort.ImageAsset asset) throws Exception {
        if (asset.b64Json() != null && !asset.b64Json().isBlank()) {
            return ImageBlock.builder()
                    .source(new Base64Source("image/png", asset.b64Json().trim()))
                    .build();
        }
        if (asset.url() != null && !asset.url().isBlank()) {
            String url = asset.url().trim();
            if (!url.startsWith("https://")) throw BizException.of(ResultCode.PARAM_INVALID, "改图返回的 url 必须为 https");
            return ImageBlock.builder()
                    .source(new Base64Source("image/png", asset.url()))
                    .build();
        }
        throw BizException.of(ResultCode.INTERNAL_ERROR, "改图结果缺少 url/b64_json");
    }
}
