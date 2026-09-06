package com.wshake.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wshake.api.dto.CreateMcpDraftRequest;
import com.wshake.api.dto.McpOauthCallbackRequest;
import com.wshake.api.dto.RejectMcpDraftRequest;
import com.wshake.api.dto.StartMcpOauthRequest;
import com.wshake.api.dto.UpdateMcpDraftRequest;
import com.wshake.api.vo.McpDraftVO;
import com.wshake.api.vo.McpOauthVO;
import com.wshake.api.vo.McpReleaseVO;
import com.wshake.api.vo.McpVerifyResultVO;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.Result;
import com.wshake.service.mcp.McpControlService;
import com.wshake.service.mcp.McpControlService.CreateMcpCommand;
import com.wshake.service.mcp.McpControlService.McpReleaseView;
import com.wshake.service.mcp.McpControlService.McpVerifyResult;
import com.wshake.service.mcp.McpControlService.UpdateMcpCommand;
import com.wshake.service.mcp.McpOauthService;
import com.wshake.service.mcp.McpOauthService.StartLoginResult;
import com.wshake.service.mcp.McpOauthService.TokenStatus;
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
    private final McpOauthService oauthService;
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
                null,
                req.getAuthType(),
                req.getOauthClientId(),
                req.getPlainOauthClientSecret(),
                req.getOauthScope(),
                req.getOauthRequireLogin());
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
                req.getRemark(),
                req.getAuthType(),
                req.getOauthClientId(),
                req.getPlainOauthClientSecret(),
                req.getOauthScope(),
                req.getOauthRequireLogin());
        return Result.ok(converter.convert(mcpService.updateDraft(id, cmd), McpDraftVO.class));
    }

    @DeleteMapping("/draft/{id}")
    @Operation(summary = "软删 MCP 草稿")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        mcpService.softDeleteDraft(id);
        return Result.ok(null);
    }

    @PostMapping("/draft/{id}/verify")
    @Operation(summary = "握手验证(返回工具目录,不改状态;需 OAuth 时 data 带授权地址)")
    public Result<McpVerifyResultVO> verify(@PathVariable Long id) {
        try {
            // 草稿无 Release、无用户 token 概念：沿用无 token 探测（登录校验走 approve/release 侧）
            return Result.ok(toVerifyResult(mcpService.verify(id)));
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
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
    @Operation(summary = "通过审核并发布 Release(再次握手冻结连接配置;OAuth 草稿用审核人登录态校验)")
    public Result<McpReleaseVO> approve(@PathVariable Long id) {
        Long publisherUserId = StpUtil.getLoginIdAsLong();
        // 审核人若已对同名 Release 完成 OAuth 登录，approve 时用其 token 做登录校验
        String publisherAccessToken = resolvePublisherAccessToken(id, publisherUserId);
        McpReleaseView view = mcpService.approve(id, publisherUserId, publisherAccessToken);
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

    // ---------- OAuth 登录（Authorization Code + PKCE） ----------

    @PostMapping("/release/{id}/oauth/start")
    @Operation(summary = "发起 MCP OAuth 登录", description = "DCR(可选)+拼授权地址+存一次性 state；前端打开返回地址完成登录")
    public Result<McpOauthVO.StartLoginVO> startOauth(
            @PathVariable Long id, @Valid @RequestBody StartMcpOauthRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        StartLoginResult result = oauthService.startLogin(id, userId, req.getRedirectUri());
        return Result.ok(new McpOauthVO.StartLoginVO(result.authorizationUrl(), result.state()));
    }

    @PostMapping("/oauth/callback")
    @Operation(summary = "MCP OAuth 回调换票", description = "前端从回调地址取 code/state 后转交后端换票落库")
    public Result<Void> oauthCallback(@Valid @RequestBody McpOauthCallbackRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        oauthService.callback(req.getCode(), req.getState(), userId);
        return Result.ok(null);
    }

    @GetMapping("/release/{id}/oauth/status")
    @Operation(summary = "MCP OAuth 登录态", description = "当前用户在该 Release 上的 token 有效性")
    public Result<McpOauthVO.TokenStatusVO> oauthStatus(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        TokenStatus status = oauthService.status(id, userId);
        return Result.ok(
                new McpOauthVO.TokenStatusVO(status.loggedIn(), status.expired(), status.expiresAt(), status.scope()));
    }

    @PostMapping("/release/{id}/oauth/refresh")
    @Operation(summary = "刷新 MCP OAuth token", description = "refresh_token 换票")
    public Result<McpOauthVO.TokenStatusVO> oauthRefresh(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        TokenStatus status = oauthService.refresh(id, userId);
        return Result.ok(
                new McpOauthVO.TokenStatusVO(status.loggedIn(), status.expired(), status.expiresAt(), status.scope()));
    }

    @DeleteMapping("/release/{id}/oauth/token")
    @Operation(summary = "解绑 MCP OAuth 授权", description = "删除当前用户在该 Release 上的 token")
    public Result<Void> oauthRevoke(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        oauthService.revoke(id, userId);
        return Result.ok(null);
    }

    /** approve 时解析审核人 token：找同名最新 PUBLISHED Release 上的用户 token（草稿尚未发布时通常为空）。 */
    private String resolvePublisherAccessToken(Long draftId, Long publisherUserId) {
        try {
            McpControlService.McpDraftView draft = mcpService.getDraft(draftId);
            if (draft == null || !"OAUTH".equalsIgnoreCase(draft.authType())) {
                return null;
            }
            for (McpReleaseView release : mcpService.listBindable(publisherUserId)) {
                if (release != null
                        && draft.name().equals(release.name())
                        && "OAUTH".equalsIgnoreCase(release.authType())) {
                    String token = oauthService.accessTokenFor(release.id(), publisherUserId);
                    if (token != null && !token.isBlank()) {
                        return token;
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败不阻塞 approve（service 侧按未登录处理）
        }
        return null;
    }

    private static McpVerifyResultVO toVerifyResult(McpVerifyResult result) {
        List<McpVerifyResultVO.McpToolEntryVO> items = new ArrayList<>();
        List<McpToolEntry> tools = result.tools() == null ? List.of() : result.tools();
        for (McpToolEntry t : tools) {
            items.add(new McpVerifyResultVO.McpToolEntryVO(t.name(), t.description(), t.inputSchema(), t.readOnly()));
        }
        McpVerifyResultVO vo = new McpVerifyResultVO();
        vo.setSuccess(result.success());
        vo.setMessage(result.message());
        vo.setToolCount(result.toolCount());
        vo.setTools(items);
        vo.setOauthAuthorizationUrl(result.oauthAuthorizationUrl());
        vo.setOauthScope(result.oauthScope());
        vo.setOauthResource(result.oauthResource());
        vo.setOauthResourceMetadataUrl(result.oauthResourceMetadataUrl());
        return vo;
    }
}
