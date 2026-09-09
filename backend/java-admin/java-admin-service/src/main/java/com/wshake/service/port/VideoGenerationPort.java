package com.wshake.service.port;

import java.util.Map;

/** 文生/图生视频端口；厂商差异留在 infra 适配器。 */
public interface VideoGenerationPort {

    VideoGenResult generate(VideoGenCommand command);

    record VideoGenCommand(
            String provider,
            String baseUrl,
            String modelName,
            String plainSecret,
            String prompt,
            Integer durationSeconds,
            String aspectRatio,
            String resolution,
            String imageUrl) {}

    record VideoGenResult(String url, String requestId, Integer durationSeconds, Map<String, Object> raw) {}
}
