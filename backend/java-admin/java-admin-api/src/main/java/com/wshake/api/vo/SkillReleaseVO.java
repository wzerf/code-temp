package com.wshake.api.vo;

import com.wshake.service.agent.SkillControlModels.SkillReleaseView;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillReleaseView.class)
public class SkillReleaseVO {

    private Long id;
    private String name;
    private Integer version;
    private String description;
    private String skillContent;
    private String visibility;
    private String status;
    private Long ownerUserId;
    private Long sourceDraftId;
    private String contentHash;
    private String source;
    private String remark;
    private List<SkillResourceVO> resources;
    private LocalDateTime createdAt;
}
