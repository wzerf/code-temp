package com.wshake.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git Skill 来源 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Git Skill 来源")
public class GitSourceVO {

    private Long id;
    private String scope;
    private Long ownerUserId;
    private String url;
    private String ref;
    private String subdirectory;
    private String lastCommitSha;
    private LocalDateTime lastSyncedAt;
    /** READY=正常 / FAILED=同步失败 */
    private String status;

    private String lastError;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
