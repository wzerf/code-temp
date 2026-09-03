package com.wshake.api.vo;

import com.wshake.service.agent.AgentSessionService.SessionSkillBindingView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Session Skill 绑定 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SessionSkillBindingView.class)
@Schema(description = "会话 Skill 绑定")
public class SessionSkillBindingVO {

    private Long id;
    private Long sessionId;
    private Long skillReleaseId;
    private String skillName;
    private String contentHash;
}
