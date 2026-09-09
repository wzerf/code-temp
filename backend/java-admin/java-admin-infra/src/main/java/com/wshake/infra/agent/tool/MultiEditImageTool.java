package com.wshake.infra.agent.tool;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.infra.agent.runtime.AgentContext;
import com.wshake.infra.imagegen.ImageGenAdapterRegistry;
import com.wshake.infra.imagegen.ImageGenProperties;
import com.wshake.service.port.ImageGenerationPort;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultiEditImageTool implements AgentTool {

    private final ImageGenAdapterRegistry adapterRegistry;
    private final ImageGenProperties imageGenProperties;

    @Override
    public String getName() {
        return "multi_edit_image";
    }

    @Override
    public String getDescription() {
        return "多图融合：基于多张参考图 image_urls 融合改图，用于\"把这几张图融合/按这几张图拼一张\"。与 edit_image 互斥，不要用于单图微改。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("prompt", Map.of("type", "string", "description", "融合编辑指令，1..4000 字符"));
        props.put(
                "image_urls",
                Map.of("type", "array", "items", Map.of("type", "string"), "description", "参考图 https URL 列表，2..4 张"));
        props.put(
                "aspect_ratio", Map.of("type", "string", "enum", List.of("1:1", "16:9", "9:16", "4:3", "3:4", "auto")));
        props.put("resolution", Map.of("type", "string", "enum", List.of("1k", "2k")));
        schema.put("properties", props);
        schema.put("required", List.of("prompt", "image_urls"));
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        var capturedModel = AgentContext.imageModelOrNull(param);
        if (capturedModel == null) capturedModel = AgentContext.imageModelOrNull();
        if (capturedModel != null && !capturedModel.isConfigured()) capturedModel = null;
        Map<String, Object> input = param.getInput() == null ? Map.of() : param.getInput();
        String prompt = input.get("prompt") == null
                ? ""
                : String.valueOf(input.get("prompt")).trim();
        if (prompt.isEmpty()) return Mono.just(ToolResultBlock.error("prompt 不能为空"));
        if (prompt.length() > imageGenProperties.getMaxPromptChars()) {
            return Mono.just(ToolResultBlock.error("prompt 过长，上限 " + imageGenProperties.getMaxPromptChars()));
        }
        Object raw = input.get("image_urls");
        List<String> urls = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String u = String.valueOf(o).trim();
                if (!u.isBlank()) urls.add(u);
            }
        }
        if (urls.size() < 2) return Mono.just(ToolResultBlock.error("image_urls 至少需要 2 张图"));
        for (String u : urls) {
            if (!u.startsWith("https://")) return Mono.just(ToolResultBlock.error("image_urls 必须为 https: " + u));
        }
        String aspectRatio = input.get("aspect_ratio") == null
                ? null
                : String.valueOf(input.get("aspect_ratio")).trim();
        if (aspectRatio != null && aspectRatio.isBlank()) aspectRatio = null;
        String resolution = input.get("resolution") == null
                ? null
                : String.valueOf(input.get("resolution")).trim();
        if (resolution != null && resolution.isBlank()) resolution = null;
        final String fp = prompt;
        final List<String> fu = List.copyOf(urls);
        final String fa = aspectRatio;
        final String fr = resolution;
        final var fc = capturedModel;
        return Mono.fromCallable(() -> doEdit(fp, fu, fa, fr, fc)).onErrorResume(e -> {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("multi_edit_image failed: {}", msg);
            if (e instanceof BizException be) return Mono.just(ToolResultBlock.error(be.getMessage()));
            return Mono.just(ToolResultBlock.error("多图改图失败: " + msg));
        });
    }

    private ToolResultBlock doEdit(
            String prompt,
            List<String> imageUrls,
            String aspectRatio,
            String resolution,
            com.wshake.infra.agent.runtime.AgentRunPlan.ImageModelConfig captured)
            throws Exception {
        var imageModel = captured;
        if (imageModel == null || !imageModel.isConfigured()) imageModel = AgentContext.imageModelOrNull();
        if (imageModel == null || !imageModel.isConfigured()) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "当前 Agent 未配置生图模型，请在 Agent 管理中编辑草稿→勾选生图并配置 BaseUrl/ModelName/密钥→发布；已有会话需新建会话或「固定 Revision」后重试");
        }
        ImageGenerationPort port = adapterRegistry.forImageModel(imageModel);
        ImageGenerationPort.ImageGenResult result = port.generate(new ImageGenerationPort.ImageGenCommand(
                null,
                imageModel.provider(),
                imageModel.baseUrl(),
                imageModel.modelName(),
                imageModel.plainSecret(),
                prompt,
                null,
                1,
                null,
                null,
                aspectRatio,
                resolution,
                "b64_json",
                imageUrls));
        List<Object> blocks = new ArrayList<>();
        for (ImageGenerationPort.ImageAsset asset : result.images()) {
            blocks.add(toImageBlock(asset));
        }
        if (blocks.isEmpty()) throw BizException.of(ResultCode.INTERNAL_ERROR, "多图改图返回为空");
        String text = result.revisedPrompt() != null && !result.revisedPrompt().isBlank()
                ? "revised_prompt: " + result.revisedPrompt() + " | model: " + imageModel.modelName()
                : "model: " + imageModel.modelName();
        blocks.add(TextBlock.builder().text(text).build());
        @SuppressWarnings("unchecked")
        List blocksTyped = blocks;
        return ToolResultBlock.of(blocksTyped);
    }

    private ImageBlock toImageBlock(ImageGenerationPort.ImageAsset asset) {
        if (asset.b64Json() != null && !asset.b64Json().isBlank()) {
            return ImageBlock.builder()
                    .source(new Base64Source("image/png", asset.b64Json().trim()))
                    .build();
        }
        if (asset.url() != null && !asset.url().isBlank()) {
            return ImageBlock.builder()
                    .source(new Base64Source("image/png", asset.url()))
                    .build();
        }
        throw BizException.of(ResultCode.INTERNAL_ERROR, "多图改图结果缺少 url/b64_json");
    }
}
