package com.wshake.api.vo;

import com.wshake.service.agent.AgentControlService.SkillBindingView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Revision Skill 绑定 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SkillBindingView.class)
@Schema(description = "Revision Skill 绑定")
public class RevisionSkillBindingVO {

    private Long id;
    private Long agentRevisionId;
    private Long skillReleaseId;
    private String skillName;
    private String contentHash;
    private Integer overrideWinner;
}
