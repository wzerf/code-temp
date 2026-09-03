package com.wshake.api.controller;

import com.wshake.api.dto.McpDtos.CreateMcpDraftRequest;
import com.wshake.api.dto.McpDtos.ReviewRequest;
import com.wshake.api.dto.McpDtos.UpdateMcpDraftRequest;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.agent.mcp.McpControlService;
import com.wshake.service.agent.mcp.McpManageModels.CreateMcpDraftCommand;
import com.wshake.service.agent.mcp.McpManageModels.McpDraftListQuery;
import com.wshake.service.agent.mcp.McpManageModels.McpDraftView;
import com.wshake.service.agent.mcp.McpManageModels.McpReleaseView;
import com.wshake.service.agent.mcp.McpManageModels.ProbeResult;
import com.wshake.service.agent.mcp.McpManageModels.ReviewCommand;
import com.wshake.service.agent.mcp.McpManageModels.UpdateMcpDraftCommand;
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
 * MCP 管理 / 市场（路径 {@code /api/agent/mcp/*}）。
 *
 * @author wshake
 */
@Tag(name = "MCP 管理", description = "草稿/验证/审核/Release/市场派生")
@RestController
@RequestMapping("/api/agent/mcp")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class McpController {

    private final McpControlService mcpControlService;

    @GetMapping("/list")
    @Operation(summary = "MCP 草稿分页")
    public Result<PageData<McpDraftView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        return Result.ok(mcpControlService.pageDrafts(
                McpDraftListQuery.of(page, pageSize, ownerUserId, name, visibility, status)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "MCP 草稿详情")
    public Result<McpDraftView> detail(@PathVariable Long id) {
        return Result.ok(mcpControlService.getDraft(id));
    }

    @PostMapping
    @Operation(summary = "创建 MCP 草稿")
    public Result<McpDraftView> create(@Valid @RequestBody CreateMcpDraftRequest req) {
        return Result.ok(mcpControlService.createDraft(new CreateMcpDraftCommand(
                req.getName(),
                req.getTransport(),
                req.getUrl(),
                req.getHeaders(),
                req.getEncryptedSecret(),
                req.getConnectTimeoutMs(),
                req.getVisibility(),
                req.getOwnerUserId(),
                req.getRemark(),
                req.getIsEnabled())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 MCP 草稿")
    public Result<McpDraftView> update(@PathVariable Long id, @RequestBody UpdateMcpDraftRequest req) {
        return Result.ok(mcpControlService.updateDraft(new UpdateMcpDraftCommand(
                id,
                req.getName(),
                req.getTransport(),
                req.getUrl(),
                req.getHeaders(),
                req.getEncryptedSecret(),
                req.getConnectTimeoutMs(),
                req.getVisibility(),
                req.getOwnerUserId(),
                req.getRemark(),
                req.getIsEnabled())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删 MCP 草稿")
    public Result<McpDraftView> delete(@PathVariable Long id) {
        return Result.ok(mcpControlService.softDelete(id));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "验证 MCP 连接")
    public Result<ProbeResult> verify(@PathVariable Long id) {
        return Result.ok(mcpControlService.verify(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交 MCP 审核")
    public Result<McpDraftView> submit(@PathVariable Long id) {
        return Result.ok(mcpControlService.submit(id));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "审核 MCP 草稿")
    public Result<McpReleaseView> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return Result.ok(mcpControlService.review(id, new ReviewCommand(req.getAction(), req.getComment())));
    }

    @GetMapping("/release/list")
    @Operation(summary = "MCP Release 列表")
    public Result<PageData<McpReleaseView>> releaseList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        int p = page == null || page < 1 ? 1 : page;
        int s = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return Result.ok(mcpControlService.pageReleases(p, s, ownerUserId, name, visibility, status));
    }

    @GetMapping("/release/{id}")
    @Operation(summary = "MCP Release 详情")
    public Result<McpReleaseView> releaseDetail(@PathVariable Long id) {
        return Result.ok(mcpControlService.getRelease(id));
    }

    @PostMapping("/release/{id}/deprecate")
    @Operation(summary = "弃用 MCP Release")
    public Result<McpReleaseView> deprecate(@PathVariable Long id) {
        return Result.ok(mcpControlService.deprecate(id));
    }

    @GetMapping("/market")
    @Operation(summary = "MCP 市场列表")
    public Result<List<McpReleaseView>> market() {
        return Result.ok(mcpControlService.market());
    }
}
