package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.SkillResourceView;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillResourceView.class)
public class SkillResourceVO {

    private Long id;
    private String path;
    private String content;
    private String contentHash;
}
