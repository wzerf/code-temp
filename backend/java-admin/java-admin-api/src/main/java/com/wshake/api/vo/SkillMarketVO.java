package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.SkillMarketView;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillMarketView.class)
public class SkillMarketVO {

    private Long id;
    private String name;
    private String description;
    private String contentHash;
    private Long currentReleaseId;
    private String source;
}
