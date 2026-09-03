package com.wshake.api.vo;

import com.wshake.service.skill.SkillControlService.SkillReleaseView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill Release VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillReleaseView.class)
@Schema(description = "Skill Release(不可变快照)")
public class SkillReleaseVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String visibility;
    private String status;
    private Integer version;
    private String description;
    private String skillContent;
    private String contentHash;
    private Long sourceDraftId;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    /** 资源文件数(SKILL.md 之外)。 */
    private Integer resourceCount;
}
