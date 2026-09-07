package com.wshake.infra.imagegen;

import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.port.ImageGenerationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageGenAdapterRegistry {

    private final OpenAiImageAdapter openAiImageAdapter;
    private final XaiImageAdapter xaiImageAdapter;

    public ImageGenerationPort forRelease(AgentModelRelease release) {
        String baseUrl =
                release.getBaseUrl() == null ? "" : release.getBaseUrl().toLowerCase();
        if (baseUrl.contains("api.x.ai")) {
            return xaiImageAdapter;
        }
        return openAiImageAdapter;
    }
}
