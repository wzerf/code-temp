package com.wshake.service.port;

import java.util.List;
import java.util.Map;

public interface ImageGenerationPort {

    ImageGenResult generate(ImageGenCommand command);

    ProbeResult probe(ProbeCommand command);

    record ImageGenCommand(
            Long modelReleaseId,
            String provider,
            String baseUrl,
            String modelName,
            String plainSecret,
            String prompt,
            String size,
            Integer n,
            String quality,
            String style,
            String aspectRatio,
            String resolution,
            String responseFormat,
            List<String> referenceImageUrls) {

        public ImageGenCommand(
                Long modelReleaseId,
                String provider,
                String baseUrl,
                String modelName,
                String plainSecret,
                String prompt,
                String size,
                Integer n,
                String quality,
                String style) {
            this(
                    modelReleaseId,
                    provider,
                    baseUrl,
                    modelName,
                    plainSecret,
                    prompt,
                    size,
                    n,
                    quality,
                    style,
                    null,
                    null,
                    null,
                    null);
        }
    }

    record ImageGenResult(List<ImageAsset> images, String revisedPrompt, Map<String, Object> raw) {}

    record ImageAsset(String url, String b64Json, String mimeType, Integer width, Integer height) {}

    record ProbeCommand(String provider, String baseUrl, String modelName, String plainSecret) {}

    record ProbeResult(boolean ok, String message) {}
}
