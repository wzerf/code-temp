package com.wshake.api.vo;

import java.util.List;

public record GitSkillPreviewVO(Long sourceId, String commitSha, List<Item> skills) {
    public record Item(
            String skillPath, String name, String description, String contentHash, int resourceCount, long totalBytes) {}
}
