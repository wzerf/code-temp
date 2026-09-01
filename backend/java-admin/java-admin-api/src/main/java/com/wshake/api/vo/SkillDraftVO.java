package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.SkillDraftView;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillDraftView.class)
public class SkillDraftVO {

    private Long id;
    private String name;
    private String description;
    private String skillContent;
    private String visibility;
    private String status;
    private Long ownerUserId;
    private Long basedOnReleaseId;
    private String contentHash;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String remark;
    private List<SkillResourceVO> resources;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
