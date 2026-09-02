package com.wshake.api.vo;

import java.util.List;

public record GitSkillSyncResultVO(Long sourceId, String commitSha, List<Item> results) {
    public record Item(String skillPath, String name, String status, Long draftId, String message) {}
}
