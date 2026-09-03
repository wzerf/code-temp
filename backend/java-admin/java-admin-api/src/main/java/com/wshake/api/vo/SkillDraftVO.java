package com.wshake.api.vo;

import com.wshake.service.skill.SkillControlService.SkillView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 草稿 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillView.class)
@Schema(description = "Skill 草稿")
public class SkillDraftVO {

    private Long id;
    private Long ownerUserId;
    private String name;
    private String visibility;
    private String status;
    private String description;
    private String skillContent;
    private String contentHash;
    private Long basedOnReleaseId;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String remark;
    private Integer isEnabled;
    private Long deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    /** 资源文件数(SKILL.md 之外)。 */
    private Integer resourceCount;
    /** Git 来源分组(空=手动创建)。 */
    private String groupKey;
}
