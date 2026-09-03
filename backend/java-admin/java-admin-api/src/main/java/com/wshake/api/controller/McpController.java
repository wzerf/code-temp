package com.wshake.api.controller;

import com.wshake.api.dto.CreateMcpDraftRequest;
import com.wshake.api.dto.RejectMcpDraftRequest;
import com.wshake.api.dto.UpdateMcpDraftRequest;
import com.wshake.api.vo.McpDraftVO;
import com.wshake.api.vo.McpReleaseVO;
import com.wshake.api.vo.McpVerifyResultVO;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.mcp.McpControlService;
import com.wshake.service.mcp.McpControlService.CreateMcpCommand;
import com.wshake.service.mcp.McpControlService.McpReleaseView;
import com.wshake.service.mcp.McpControlService.UpdateMcpCommand;
import com.wshake.service.port.McpProbePort.McpToolEntry;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * MCP 管理/市场（路径 {@code /api/system/mcp/*}）。
 *
 * @author wshake
 */
@Tag(name = "MCP 管理", description = "草稿 CRUD/verify/submit/approve/reject + Release + 市场")
@RestController
@RequestMapping("/api/system/mcp")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class McpController {

    private final McpControlService mcpService;
    private final Converter converter;

    @GetMapping("/draft/list")
    @Operation(summary = "MCP 草稿分页", description = "data={items,total}")
    public Result<PageData<McpDraftVO>> draftList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        PageData<McpControlService.McpDraftView> pd = mcpService.pageDraft(
                McpControlService.McpListQuery.of(page, pageSize, ownerUserId, name, visibility, status));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), McpDraftVO.class), pd.getTotal()));
    }

    @GetMapping("/draft/all")
    @Operation(summary = "MCP 草稿全量")
    public Result<List<McpDraftVO>> draftAll(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status) {
        PageData<McpControlService.McpDraftView> pd = mcpService.pageDraft(
                McpControlService.McpListQuery.of(1, Integer.MAX_VALUE, ownerUserId, name, visibility, status));
        return Result.ok(converter.convert(pd.getItems(), McpDraftVO.class));
    }

    @GetMapping("/draft/{id}")
    @Operation(summary = "MCP 草稿详情")
    public Result<McpDraftVO> draftDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(mcpService.getDraft(id), McpDraftVO.class));
    }

    @PostMapping("/draft")
    @Operation(summary = "创建 MCP 草稿")
    public Result<McpDraftVO> createDraft(@Valid @RequestBody CreateMcpDraftRequest req) {
        CreateMcpCommand cmd = new CreateMcpCommand(
                req.getName(),
                req.getTransport(),
                req.getUrl(),
                req.getHeadersJson(),
                req.getVisibility(),
                req.getPlainSecret(),
                req.getConnectTimeoutMs(),
                req.getRemark(),
                null);
        return Result.ok(converter.convert(mcpService.createDraft(cmd), McpDraftVO.class));
    }

    @PutMapping("/draft/{id}")
    @Operation(summary = "更新 MCP 草稿")
    public Result<McpDraftVO> updateDraft(@PathVariable Long id, @Valid @RequestBody UpdateMcpDraftRequest req) {
        UpdateMcpCommand cmd = new UpdateMcpCommand(
                req.getName(),
                req.getTransport(),
                req.getUrl(),
                req.getHeadersJson(),
                req.getPlainSecret(),
                req.getConnectTimeoutMs(),
                req.getRemark());
        return Result.ok(converter.convert(mcpService.updateDraft(id, cmd), McpDraftVO.class));
    }

    @DeleteMapping("/draft/{id}")
    @Operation(summary = "软删 MCP 草稿")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        mcpService.softDeleteDraft(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/verify")
    @Operation(summary = "握手验证(返回工具目录,不改状态)")
    public Result<McpVerifyResultVO> verify(@PathVariable Long id) {
        List<McpToolEntry> tools;
        try {
            tools = mcpService.verify(id);
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
        return Result.ok(toVerifyResult(true, "握手成功", tools));
    }

    @PostMapping("/draft/{id}/submit")
    @Operation(summary = "提交审核")
    public Result<Void> submit(@PathVariable Long id) {
        mcpService.submit(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/withdraw")
    @Operation(summary = "撤回审核")
    public Result<Void> withdraw(@PathVariable Long id) {
        mcpService.withdraw(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/approve")
    @Operation(summary = "通过审核并发布 Release(再次握手冻结连接配置)")
    public Result<McpReleaseVO> approve(@PathVariable Long id) {
        McpReleaseView view = mcpService.approve(id);
        return Result.ok(converter.convert(view, McpReleaseVO.class));
    }

    @PostMapping("/draft/{id}/reject")
    @Operation(summary = "驳回草稿")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) RejectMcpDraftRequest req) {
        mcpService.reject(id, req == null ? "" : req.getReason());
        return Result.ok(null);
    }

    @GetMapping("/release/list")
    @Operation(summary = "MCP Release 分页")
    public Result<PageData<McpReleaseVO>> releaseList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name) {
        PageData<McpReleaseView> pd =
                mcpService.pageRelease(McpControlService.ReleaseListQuery.of(page, pageSize, visibility, status, name));
        return Result.ok(PageData.of(converter.convert(pd.getItems(), McpReleaseVO.class), pd.getTotal()));
    }

    @GetMapping("/release/{id}")
    @Operation(summary = "MCP Release 详情")
    public Result<McpReleaseVO> releaseDetail(@PathVariable Long id) {
        return Result.ok(converter.convert(mcpService.getRelease(id), McpReleaseVO.class));
    }

    @GetMapping("/market")
    @Operation(summary = "MCP 市场列表")
    public Result<List<McpReleaseVO>> market() {
        return Result.ok(converter.convert(mcpService.listMarket(), McpReleaseVO.class));
    }

    @GetMapping("/bindable")
    @Operation(summary = "可绑定候选(MARKET 全量 + 本人 PRIVATE 最新)")
    public Result<List<McpReleaseVO>> bindable(@RequestParam(required = false) Long ownerUserId) {
        return Result.ok(converter.convert(mcpService.listBindable(ownerUserId), McpReleaseVO.class));
    }

    @PostMapping("/market/{id}/take-down")
    @Operation(summary = "市场下架(置 DEPRECATED)")
    public Result<Void> takeDown(@PathVariable Long id) {
        mcpService.takeDownMarket(id);
        return Result.ok(null);
    }

    @PostMapping("/release/{id}/deprecate")
    @Operation(summary = "弃用单个 Release")
    public Result<Void> deprecate(@PathVariable Long id) {
        mcpService.deprecate(id);
        return Result.ok(null);
    }

    private static McpVerifyResultVO toVerifyResult(boolean success, String message, List<McpToolEntry> tools) {
        List<McpVerifyResultVO.McpToolEntryVO> items = new ArrayList<>();
        for (McpToolEntry t : tools) {
            items.add(new McpVerifyResultVO.McpToolEntryVO(t.name(), t.description(), t.inputSchema(), t.readOnly()));
        }
        return new McpVerifyResultVO(success, message, items.size(), items);
    }
}
