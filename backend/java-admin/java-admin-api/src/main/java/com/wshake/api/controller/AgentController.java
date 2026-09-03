package com.wshake.api.controller;

import com.wshake.api.dto.BindMcpRequest;
import com.wshake.api.dto.BindSkillRequest;
import com.wshake.api.dto.CreateAgentRequest;
import com.wshake.api.dto.SaveAgentRevisionRequest;
import com.wshake.api.dto.UpdateAgentRequest;
import com.wshake.api.vo.AgentRevisionVO;
import com.wshake.api.vo.AgentVO;
import com.wshake.api.vo.RevisionMcpBindingVO;
import com.wshake.api.vo.RevisionSkillBindingVO;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.agent.AgentControlService;
import com.wshake.service.agent.AgentControlService.AgentDefinitionView;
import com.wshake.service.agent.AgentControlService.AgentListQuery;
import com.wshake.service.agent.AgentControlService.AgentRevisionView;
import com.wshake.service.agent.AgentControlService.BindMcpCommand;
import com.wshake.service.agent.AgentControlService.BindSkillCommand;
import com.wshake.service.agent.AgentControlService.CreateAgentCommand;
import com.wshake.service.agent.AgentControlService.DraftRevisionCommand;
import com.wshake.service.agent.AgentControlService.UpdateAgentCommand;
import com.wshake.service.agent.AgentSecretCipher;
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
 * Agent 管理（路径 {@code /api/system/agent/*}）。
 *
 * @author wshake
 */
@Tag(name = "Agent 管理", description = "定义 + Revision 草稿/发布/回滚/禁用 + Revision 绑定")
@RestController
@RequestMapping("/api/system/agent")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgentController {

    private final AgentControlService agentService;
    private final AgentSecretCipher secretCipher;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "Agent 定义分页")
    public Result<PageData<AgentVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer isEnabled) {
        PageData<AgentDefinitionView> pd = agentService.page(AgentListQuery.of(page, pageSize, name, isEnabled));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), AgentVO.class), pd.getTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "Agent 定义全量")
    public Result<List<AgentVO>> all(@RequestParam(required = false) Integer isEnabled) {
        return Result.ok(converter.convert(agentService.listAll(isEnabled), AgentVO.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Agent 定义详情")
    public Result<AgentVO> detail(@PathVariable Long id) {
        return Result.ok(converter.convert(agentService.getById(id), AgentVO.class));
    }

    @PostMapping
    @Operation(summary = "创建 Agent 定义")
    public Result<AgentVO> create(@Valid @RequestBody CreateAgentRequest req) {
        CreateAgentCommand cmd = new CreateAgentCommand(
                req.getName(), req.getDescription(), req.getRemark(), req.getOwnerUserId(), req.getIsEnabled());
        return Result.ok(converter.convert(agentService.create(cmd), AgentVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent 定义")
    public Result<AgentVO> update(@PathVariable Long id, @Valid @RequestBody UpdateAgentRequest req) {
        UpdateAgentCommand cmd = new UpdateAgentCommand(req.getName(), req.getDescription(), req.getRemark());
        return Result.ok(converter.convert(agentService.update(id, cmd), AgentVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 Agent 定义")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.softDelete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "紧急禁用(只阻止新会话/首启)")
    public Result<AgentVO> disable(@PathVariable Long id) {
        return Result.ok(converter.convert(agentService.disable(id), AgentVO.class));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 Agent")
    public Result<AgentVO> enable(@PathVariable Long id) {
        return Result.ok(converter.convert(agentService.enable(id), AgentVO.class));
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚到指定已发布 Revision")
    public Result<AgentVO> rollback(@PathVariable Long id, @RequestBody RollbackRequest req) {
        return Result.ok(converter.convert(agentService.rollback(id, req.revisionId), AgentVO.class));
    }

    // ---------- Revision ----------

    @GetMapping("/{id}/revisions")
    @Operation(summary = "Agent Revision 列表")
    public Result<List<AgentRevisionVO>> revisions(@PathVariable Long id) {
        return Result.ok(converter.convert(agentService.listRevisions(id), AgentRevisionVO.class));
    }

    @GetMapping("/{id}/revisions/active-draft")
    @Operation(summary = "当前活跃草稿(无则 data=null)")
    public Result<AgentRevisionVO> activeDraft(@PathVariable Long id) {
        AgentRevisionView view = agentService.getActiveDraft(id);
        return Result.ok(view == null ? null : converter.convert(view, AgentRevisionVO.class));
    }

    @PostMapping("/{id}/revisions")
    @Operation(summary = "创建草稿 Revision")
    public Result<AgentRevisionVO> createRevision(
            @PathVariable Long id, @Valid @RequestBody SaveAgentRevisionRequest req) {
        AgentRevisionView view = agentService.createDraftRevision(id, toDraftCommand(req));
        return Result.ok(converter.convert(view, AgentRevisionVO.class));
    }

    @PutMapping("/revisions/{revisionId}")
    @Operation(summary = "更新草稿 Revision")
    public Result<AgentRevisionVO> updateRevision(
            @PathVariable Long revisionId, @Valid @RequestBody SaveAgentRevisionRequest req) {
        return Result.ok(converter.convert(
                agentService.updateDraftRevision(revisionId, toDraftCommand(req)), AgentRevisionVO.class));
    }

    @DeleteMapping("/revisions/{revisionId}")
    @Operation(summary = "删除草稿 Revision")
    public Result<Void> deleteRevision(@PathVariable Long revisionId) {
        agentService.deleteDraftRevision(revisionId);
        return Result.ok(null);
    }

    @PostMapping("/revisions/{revisionId}/publish")
    @Operation(summary = "发布草稿(copyAsPublished + 复制绑定 + 更新指针)")
    public Result<AgentRevisionVO> publish(@PathVariable Long revisionId) {
        return Result.ok(converter.convert(agentService.publish(revisionId), AgentRevisionVO.class));
    }

    // ---------- Revision Skill/MCP 绑定 ----------

    @GetMapping("/revisions/{revisionId}/skill-bindings")
    @Operation(summary = "Revision Skill 绑定列表")
    public Result<List<RevisionSkillBindingVO>> skillBindings(@PathVariable Long revisionId) {
        return Result.ok(
                converter.convert(agentService.listRevisionSkillBindings(revisionId), RevisionSkillBindingVO.class));
    }

    @PostMapping("/revisions/{revisionId}/skill-bindings")
    @Operation(summary = "绑定 Skill 到 Revision")
    public Result<RevisionSkillBindingVO> bindSkill(
            @PathVariable Long revisionId, @Valid @RequestBody BindSkillRequest req) {
        BindSkillCommand cmd = new BindSkillCommand(
                req.getSkillReleaseId(), req.getSkillName(), req.getContentHash(), req.getOverrideWinner());
        return Result.ok(
                converter.convert(agentService.bindSkillToRevision(revisionId, cmd), RevisionSkillBindingVO.class));
    }

    @DeleteMapping("/revisions/{revisionId}/skill-bindings/{bindingId}")
    @Operation(summary = "解除 Revision Skill 绑定")
    public Result<Void> unbindSkill(@PathVariable Long bindingId) {
        agentService.unbindSkillFromRevision(bindingId);
        return Result.ok(null);
    }

    @GetMapping("/revisions/{revisionId}/mcp-bindings")
    @Operation(summary = "Revision MCP 绑定列表")
    public Result<List<RevisionMcpBindingVO>> mcpBindings(@PathVariable Long revisionId) {
        return Result.ok(
                converter.convert(agentService.listRevisionMcpBindings(revisionId), RevisionMcpBindingVO.class));
    }

    @PostMapping("/revisions/{revisionId}/mcp-bindings")
    @Operation(summary = "绑定 MCP 到 Revision(MARKET MCP 在此补配密钥)")
    public Result<RevisionMcpBindingVO> bindMcp(@PathVariable Long revisionId, @Valid @RequestBody BindMcpRequest req) {
        String encrypted = (req.getPlainSecret() == null || req.getPlainSecret().isEmpty())
                ? null
                : secretCipher.encrypt(req.getPlainSecret());
        BindMcpCommand cmd = new BindMcpCommand(req.getMcpReleaseId(), req.getMcpName(), encrypted);
        return Result.ok(
                converter.convert(agentService.bindMcpToRevision(revisionId, cmd), RevisionMcpBindingVO.class));
    }

    @DeleteMapping("/revisions/{revisionId}/mcp-bindings/{bindingId}")
    @Operation(summary = "解除 Revision MCP 绑定")
    public Result<Void> unbindMcp(@PathVariable Long bindingId) {
        agentService.unbindMcpFromRevision(bindingId);
        return Result.ok(null);
    }

    /** 回滚请求体。 */
    public record RollbackRequest(Long revisionId) {}

    private static DraftRevisionCommand toDraftCommand(SaveAgentRevisionRequest req) {
        return new DraftRevisionCommand(
                req.getSystemPrompt(),
                req.getModelConfig(),
                req.getPermissionPolicy(),
                req.getMemoryPolicy(),
                req.getCompressionPolicy(),
                req.getRemark());
    }
}
