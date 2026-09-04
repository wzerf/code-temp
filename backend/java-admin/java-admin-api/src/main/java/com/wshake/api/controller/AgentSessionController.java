package com.wshake.api.controller;

import com.wshake.api.dto.BindSessionMcpRequest;
import com.wshake.api.dto.BindSessionModelRequest;
import com.wshake.api.dto.BindSessionSkillRequest;
import com.wshake.api.vo.AgentSessionVO;
import com.wshake.api.vo.SessionMcpBindingVO;
import com.wshake.api.vo.SessionModelBindingVO;
import com.wshake.api.vo.SessionSkillBindingVO;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSessionService;
import com.wshake.service.agent.AgentSessionService.AgentSessionView;
import com.wshake.service.agent.AgentSessionService.BindSessionMcpCommand;
import com.wshake.service.agent.AgentSessionService.BindSessionSkillCommand;
import com.wshake.service.agent.AgentSessionService.CreateSessionCommand;
import com.wshake.service.agent.AgentSessionService.SessionListQuery;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 会话控制面（路径 {@code /api/system/agent/sessions/*} 与嵌套在 Agent 下的会话）。
 *
 * @author wshake
 */
@Tag(name = "Agent 会话", description = "会话 CRUD + Revision 固定 + Session 级 Skill/MCP/模型绑定(运行面后续实现)")
@RestController
@RequestMapping("/api/system/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentSessionController {

    private final AgentSessionService sessionService;
    private final AgentSecretCipher secretCipher;
    private final Converter converter;

    @GetMapping("/{id}/sessions")
    @Operation(summary = "Agent 会话分页")
    public Result<PageData<AgentSessionVO>> sessions(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId) {
        PageData<AgentSessionView> pd =
                sessionService.pageByAgent(id, SessionListQuery.of(page, pageSize, ownerUserId));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), AgentSessionVO.class), pd.getTotal()));
    }

    @PostMapping("/{id}/sessions")
    @Operation(summary = "创建会话(agent_revision_id 暂空,首启运行时固定)")
    public Result<AgentSessionVO> createSession(
            @PathVariable Long id, @RequestBody(required = false) CreateSessionRequest req) {
        String remark = req == null ? "" : req.remark();
        Long owner = req == null ? null : req.ownerUserId();
        return Result.ok(converter.convert(
                sessionService.createSession(id, new CreateSessionCommand(remark, owner)), AgentSessionVO.class));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "会话详情")
    public Result<AgentSessionVO> sessionDetail(@PathVariable Long sessionId) {
        return Result.ok(converter.convert(sessionService.getSession(sessionId), AgentSessionVO.class));
    }

    @PostMapping("/sessions/{sessionId}/bind-revision")
    @Operation(summary = "固定当前发布 Revision 到会话(首次运行调用;已绑定则幂等)")
    public Result<AgentSessionVO> bindRevision(@PathVariable Long sessionId) {
        return Result.ok(converter.convert(sessionService.bindSessionRevision(sessionId), AgentSessionVO.class));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话(连同会话级绑定)")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.ok(null);
    }

    // ---------- Session Skill 绑定 ----------

    @GetMapping("/sessions/{sessionId}/skill-bindings")
    @Operation(summary = "会话 Skill 绑定列表")
    public Result<List<SessionSkillBindingVO>> skillBindings(@PathVariable Long sessionId) {
        return Result.ok(
                converter.convert(sessionService.listSessionSkillBindings(sessionId), SessionSkillBindingVO.class));
    }

    @PostMapping("/sessions/{sessionId}/skill-bindings")
    @Operation(summary = "绑定 Skill 到会话(同名覆盖)")
    public Result<SessionSkillBindingVO> bindSkill(
            @PathVariable Long sessionId, @Valid @RequestBody BindSessionSkillRequest req) {
        BindSessionSkillCommand cmd =
                new BindSessionSkillCommand(req.getSkillReleaseId(), req.getSkillName(), req.getContentHash());
        return Result.ok(
                converter.convert(sessionService.bindSkillToSession(sessionId, cmd), SessionSkillBindingVO.class));
    }

    @DeleteMapping("/sessions/{sessionId}/skill-bindings/{bindingId}")
    @Operation(summary = "解除会话 Skill 绑定")
    public Result<Void> unbindSkill(@PathVariable Long bindingId) {
        sessionService.unbindSkillFromSession(bindingId);
        return Result.ok(null);
    }

    // ---------- Session MCP 绑定 ----------

    @GetMapping("/sessions/{sessionId}/mcp-bindings")
    @Operation(summary = "会话 MCP 绑定列表")
    public Result<List<SessionMcpBindingVO>> mcpBindings(@PathVariable Long sessionId) {
        return Result.ok(
                converter.convert(sessionService.listSessionMcpBindings(sessionId), SessionMcpBindingVO.class));
    }

    @PostMapping("/sessions/{sessionId}/mcp-bindings")
    @Operation(summary = "绑定 MCP 到会话(MARKET MCP 在此补配密钥;同名覆盖)")
    public Result<SessionMcpBindingVO> bindMcp(
            @PathVariable Long sessionId, @Valid @RequestBody BindSessionMcpRequest req) {
        String encrypted = (req.getPlainSecret() == null || req.getPlainSecret().isEmpty())
                ? null
                : secretCipher.encrypt(req.getPlainSecret());
        BindSessionMcpCommand cmd = new BindSessionMcpCommand(req.getMcpReleaseId(), req.getMcpName(), encrypted);
        return Result.ok(converter.convert(sessionService.bindMcpToSession(sessionId, cmd), SessionMcpBindingVO.class));
    }

    @DeleteMapping("/sessions/{sessionId}/mcp-bindings/{bindingId}")
    @Operation(summary = "解除会话 MCP 绑定")
    public Result<Void> unbindMcp(@PathVariable Long bindingId) {
        sessionService.unbindMcpFromSession(bindingId);
        return Result.ok(null);
    }

    // ---------- Session 模型选择 ----------

    @GetMapping("/sessions/{sessionId}/model-binding")
    @Operation(summary = "会话记住的模型选择")
    public Result<SessionModelBindingVO> modelBinding(@PathVariable Long sessionId) {
        var view = sessionService.getSessionModelBinding(sessionId);
        return Result.ok(view == null ? null : converter.convert(view, SessionModelBindingVO.class));
    }

    @PutMapping("/sessions/{sessionId}/model-binding")
    @Operation(summary = "记住会话模型选择(覆盖上一次)")
    public Result<SessionModelBindingVO> bindModel(
            @PathVariable Long sessionId, @Valid @RequestBody BindSessionModelRequest req) {
        return Result.ok(converter.convert(
                sessionService.bindModelToSession(sessionId, req.getModelReleaseId()), SessionModelBindingVO.class));
    }

    @DeleteMapping("/sessions/{sessionId}/model-binding")
    @Operation(summary = "清除会话模型选择(下次回落到 Revision 默认模型)")
    public Result<Void> unbindModel(@PathVariable Long sessionId) {
        sessionService.unbindModelFromSession(sessionId);
        return Result.ok(null);
    }

    /** 创建会话请求体。 */
    public record CreateSessionRequest(String remark, Long ownerUserId) {}
}
