package com.wshake.infra.agent.tool;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewImageTool implements AgentTool {

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final long CALL_TIMEOUT_SECONDS = 15;

    private final OkHttpClient okHttpClient;

    @Override
    public String getName() {
        return "view_image";
    }

    @Override
    public String getDescription() {
        return "查看图片链接内容。传入 https 的 image_url，工具会下载图片并以多模态返回给模型，使模型能够直接看见并描述图片。当用户发了外链（如 i.pinimg.com）而你提示看不到图时，必须调用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> urlProp = new LinkedHashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "图片的 https 链接，例如 https://i.pinimg.com/.../xxx.jpg");
        props.put("image_url", urlProp);
        schema.put("properties", props);
        schema.put("required", List.of("image_url"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Object raw = param.getInput() == null ? null : param.getInput().get("image_url");
        String imageUrl = raw == null ? "" : String.valueOf(raw).trim();
        if (imageUrl.isEmpty()) {
            return Mono.just(ToolResultBlock.error("image_url 不能为空"));
        }
        String validatedUrl;
        try {
            validatedUrl = requireHttpsUrl(imageUrl);
        } catch (IllegalArgumentException e) {
            return Mono.just(ToolResultBlock.error(e.getMessage()));
        }

        return Mono.fromCallable(() -> fetchAsImageBlock(validatedUrl)).onErrorResume(e -> {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("view_image failed url={} cause={}", validatedUrl, msg);
            return Mono.just(ToolResultBlock.error("图片加载失败: " + msg));
        });
    }

    private ToolResultBlock fetchAsImageBlock(String url) throws Exception {
        URI uri = new URI(url);
        rejectUnsafeHost(uri.getHost());

        OkHttpClient client = okHttpClient
                .newBuilder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
                throw new IllegalArgumentException("HTTP " + response.code() + " " + response.message());
            }
            String finalHost = response.request().url().host();
            if (!finalHost.equalsIgnoreCase(uri.getHost())) {
                rejectUnsafeHost(finalHost);
            }
            String contentType = response.header("Content-Type");
            if (contentType != null) {
                contentType = Ascii.toLowerCase(
                        Splitter.on(';').splitToList(contentType).get(0).trim());
            }
            if (contentType != null && !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("链接不是图片类型，Content-Type=" + contentType);
            }
            String lenHeader = response.header("Content-Length");
            if (lenHeader != null) {
                try {
                    long len = Long.parseLong(lenHeader.trim());
                    if (len > MAX_IMAGE_BYTES) {
                        throw new IllegalArgumentException("图片过大(" + len + " bytes)，上限 " + MAX_IMAGE_BYTES);
                    }
                } catch (NumberFormatException e) {
                    log.debug("invalid Content-Length header: {}", lenHeader);
                }
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalArgumentException("空响应体");
            }
            byte[] bytes = body.bytes();
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("图片过大(" + bytes.length + " bytes)，上限 " + MAX_IMAGE_BYTES);
            }
            if (bytes.length == 0) {
                throw new IllegalArgumentException("图片内容为空");
            }
            String mimeType = contentType;
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = guessMimeType(url);
            }
            String base64 = Base64.getEncoder().encodeToString(bytes);
            ImageBlock imageBlock = ImageBlock.builder()
                    .source(new Base64Source(mimeType, base64))
                    .build();
            TextBlock hint = TextBlock.builder()
                    .text("图片已加载(" + mimeType + ", " + bytes.length + " bytes)，请直接描述图片内容。")
                    .build();
            return ToolResultBlock.of(List.of(imageBlock, hint));
        }
    }

    private static String requireHttpsUrl(String raw) {
        URI uri;
        try {
            uri = new URI(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("仅支持 https 图片链接");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("仅支持 https 图片链接");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("url 不得包含 user-info");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("url 缺少主机");
        }
        rejectUnsafeHost(host);
        return raw;
    }

    private static void rejectUnsafeHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host);
        }
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()) {
                throw new IllegalArgumentException("图片地址指向内网/保留地址，已拒绝: " + host);
            }
        }
    }

    private static String guessMimeType(String url) {
        String lower = Ascii.toLowerCase(url);
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.contains(".jpg?") || lower.contains(".jpeg?")) {
            return "image/jpeg";
        }
        return "image/jpeg";
    }
}
