package com.wshake.infra.videogen;

import com.wshake.infra.agent.runtime.AgentRunPlan;
import com.wshake.service.port.VideoGenerationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoGenAdapterRegistry {

    private final XaiVideoAdapter xaiVideoAdapter;

    public VideoGenerationPort forVideoModel(AgentRunPlan.VideoModelConfig videoModel) {
        java.util.Objects.requireNonNull(videoModel, "videoModel");
        return xaiVideoAdapter;
    }
}
