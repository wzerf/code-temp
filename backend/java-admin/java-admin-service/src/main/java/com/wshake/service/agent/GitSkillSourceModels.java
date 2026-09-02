package com.wshake.service.agent;

import java.time.LocalDateTime;
import java.util.List;

public final class GitSkillSourceModels {
    private GitSkillSourceModels() {}

    public record CreateCommand(Long userId, boolean administrator, String scope, String url, String ref, String subdirectory, String secretRef) {}
    public record UpdateCommand(Long id, Long userId, boolean administrator, String url, boolean urlPresent, String ref, boolean refPresent, String subdirectory, boolean subdirectoryPresent, String secretRef, boolean secretRefPresent) {}
    public record SourceView(Long id, String scope, Long ownerUserId, String url, String ref, String subdirectory, boolean hasSecretRef, String lastCommitSha, LocalDateTime lastSyncedAt, String status, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PreviewItem(String skillPath, String name, String description, String contentHash, int resourceCount, long totalBytes) {}
    public record PreviewView(Long sourceId, String commitSha, List<PreviewItem> skills) {}
    public record SyncItem(String skillPath, String name, String status, Long draftId, String message) {}
    public record SyncView(Long sourceId, String commitSha, List<SyncItem> results) {}
}
