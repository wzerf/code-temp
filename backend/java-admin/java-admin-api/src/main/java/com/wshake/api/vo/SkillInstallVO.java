package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.SkillInstallView;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillInstallView.class)
public class SkillInstallVO {

    private Long id;
    private Long userId;
    private String skillName;
    private String visibility;
    private Long ownerUserId;
    private Long currentReleaseId;
}
