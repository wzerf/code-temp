package com.wshake.api.controller;

import com.wshake.api.dto.AgentDtos.CreateAgentRequest;
import com.wshake.api.dto.AgentDtos.CreateRevisionRequest;
import com.wshake.api.dto.AgentDtos.RollbackRequest;
import com.wshake.api.dto.AgentDtos.SetBindingsRequest;
import com.wshake.api.dto.AgentDtos.UpdateAgentRequest;
import com.wshake.api.dto.AgentDtos.UpdateRevisionRequest;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.agent.AgentControlModels.AgentListQuery;
import com.wshake.service.agent.AgentControlModels.AgentView;
import com.wshake.service.agent.AgentControlModels.BindingsCommand;
import com.wshake.service.agent.AgentControlModels.BindingsView;
import com.wshake.service.agent.AgentControlModels.CreateAgentCommand;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.McpBindingCommand;
import com.wshake.service.agent.AgentControlModels.RevisionView;
import com.wshake.service.agent.AgentControlModels.SkillBindingCommand;
import com.wshake.service.agent.AgentControlModels.UpdateAgentCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.agent.AgentControlService;
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
 * Agent 管理（路径 {@code /api/agent/*}）。
 *
 * @author wshake
 */
@Tag(name = "Agent 管理", description = "定义/草稿/发布/回滚/紧急禁用/绑定")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentControlService agentControlService;

    @GetMapping("/list")
    @Operation(summary = "Agent 分页列表")
    public Result<PageData<AgentView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.ok(agentControlService.page(AgentListQuery.of(page, pageSize, name, status)));
    }

    @GetMapping("/all")
    @Operation(summary = "Agent 全量列表")
    public Result<List<AgentView>> all() {
        return Result.ok(agentControlService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Agent 详情")
    public Result<AgentView> detail(@PathVariable Long id) {
        return Result.ok(agentControlService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建 Agent")
    public Result<AgentView> create(@Valid @RequestBody CreateAgentRequest req) {
        return Result.ok(agentControlService.create(new CreateAgentCommand(
                req.getName(), req.getDescription(), req.getOwnerUserId(), req.getRemark(), req.getIsEnabled())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent")
    public Result<AgentView> update(@PathVariable Long id, @RequestBody UpdateAgentRequest req) {
        return Result.ok(agentControlService.update(new UpdateAgentCommand(
                id, req.getName(), req.getDescription(), req.getOwnerUserId(), req.getRemark(), req.getIsEnabled())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 Agent")
    public Result<AgentView> delete(@PathVariable Long id) {
        return Result.ok(agentControlService.softDelete(id));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "紧急禁用 Agent")
    public Result<AgentView> disable(@PathVariable Long id) {
        return Result.ok(agentControlService.emergencyDisable(id));
    }

    // ---------- Revision ----------

    @GetMapping("/{id}/revisions")
    @Operation(summary = "Revision 列表")
    public Result<List<RevisionView>> revisions(@PathVariable Long id) {
        return Result.ok(agentControlService.listRevisions(id));
    }

    @PostMapping("/{id}/revisions")
    @Operation(summary = "创建草稿 Revision")
    public Result<RevisionView> createDraft(@PathVariable Long id, @Valid @RequestBody CreateRevisionRequest req) {
        return Result.ok(agentControlService.createDraft(new CreateRevisionCommand(
                id,
                req.getSystemPrompt(),
                req.getModelConfig(),
                req.getPermissionPolicy(),
                req.getMemoryPolicy(),
                req.getCompressionPolicy(),
                req.getRemark())));
    }

    @PutMapping("/revisions/{id}")
    @Operation(summary = "更新草稿 Revision")
    public Result<RevisionView> updateDraft(@PathVariable Long id, @RequestBody UpdateRevisionRequest req) {
        return Result.ok(agentControlService.updateDraft(new UpdateRevisionCommand(
                id,
                req.getSystemPrompt(),
                req.getModelConfig(),
                req.getPermissionPolicy(),
                req.getMemoryPolicy(),
                req.getCompressionPolicy(),
                req.getRemark())));
    }

    @PostMapping("/revisions/{id}/publish")
    @Operation(summary = "发布 Revision")
    public Result<RevisionView> publish(@PathVariable Long id) {
        return Result.ok(agentControlService.publish(id));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚 Agent 当前发布指针")
    public Result<RevisionView> rollback(@PathVariable Long id, @RequestBody RollbackRequest req) {
        return Result.ok(agentControlService.rollback(id, req.getTargetRevisionId()));
    }

    // ---------- Bindings ----------

    @GetMapping("/revisions/{id}/bindings")
    @Operation(summary = "查看 Revision 绑定")
    public Result<BindingsView> getBindings(@PathVariable Long id) {
        return Result.ok(agentControlService.getBindings(id));
    }

    @PutMapping("/revisions/{id}/bindings")
    @Operation(summary = "设置 Revision 绑定")
    public Result<BindingsView> setBindings(@PathVariable Long id, @RequestBody SetBindingsRequest req) {
        List<SkillBindingCommand> skills = req.getSkills() == null
                ? List.of()
                : req.getSkills().stream()
                        .map(s -> new SkillBindingCommand(
                                s.getSkillReleaseId(), s.getSkillName(), s.getContentHash(), s.getOverrideWinner()))
                        .toList();
        List<McpBindingCommand> mcps = req.getMcps() == null
                ? List.of()
                : req.getMcps().stream()
                        .map(m -> new McpBindingCommand(m.getMcpReleaseId(), m.getMcpName(), m.getEncryptedSecret()))
                        .toList();
        return Result.ok(agentControlService.setBindings(id, new BindingsCommand(skills, mcps)));
    }
}
