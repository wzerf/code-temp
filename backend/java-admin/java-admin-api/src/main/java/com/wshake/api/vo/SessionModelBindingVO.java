package com.wshake.api.vo;

import com.wshake.service.agent.AgentSessionService.SessionModelBindingView;
import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Session 模型选择 VO。
 *
 * @author wshake
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@AutoMapper(target = SessionModelBindingView.class)
@Schema(description = "会话模型选择")
public class SessionModelBindingVO {

    private Long id;
    private Long sessionId;
    private Long modelReleaseId;
    private String modelName;
}
