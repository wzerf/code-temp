package com.wshake.infra.imagegen;

import com.wshake.infra.agent.runtime.AgentRunPlan;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.port.ImageGenerationPort;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageGenAdapterRegistry {

    private final OpenAiImageAdapter openAiImageAdapter;
    private final XaiImageAdapter xaiImageAdapter;

    public ImageGenerationPort forRelease(AgentModelRelease release) {
        String baseUrl =
                release.getBaseUrl() == null ? "" : release.getBaseUrl().toLowerCase(Locale.ROOT);
        if (baseUrl.contains("api.x.ai")) {
            return xaiImageAdapter;
        }
        return openAiImageAdapter;
    }

    public ImageGenerationPort forImageModel(AgentRunPlan.ImageModelConfig imageModel) {
        String baseUrl =
                imageModel.baseUrl() == null ? "" : imageModel.baseUrl().toLowerCase(Locale.ROOT);
        if (baseUrl.contains("api.x.ai")) {
            return xaiImageAdapter;
        }
        return openAiImageAdapter;
    }
}
