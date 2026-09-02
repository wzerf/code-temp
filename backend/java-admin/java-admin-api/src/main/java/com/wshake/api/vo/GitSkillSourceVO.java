package com.wshake.api.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitSkillSourceVO {
    private Long id;
    private String scope;
    private Long ownerUserId;
    private String url;
    private String ref;
    private String subdirectory;
    private boolean hasSecretRef;
    private String lastCommitSha;
    private LocalDateTime lastSyncedAt;
    private String status;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
