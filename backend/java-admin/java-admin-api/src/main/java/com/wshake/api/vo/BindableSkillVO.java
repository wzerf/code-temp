package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.BindableSkillView;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = BindableSkillView.class)
public class BindableSkillVO {

    private Long skillReleaseId;
    private String name;
    private String visibility;
    private Long ownerUserId;
    private String contentHash;
    private Integer version;
}
