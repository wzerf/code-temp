package com.wshake.service.agent.mcp;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentJsonSupport;
import com.wshake.service.agent.mcp.McpManageModels.CreateMcpDraftCommand;
import com.wshake.service.agent.mcp.McpManageModels.McpDraftListQuery;
import com.wshake.service.agent.mcp.McpManageModels.McpDraftView;
import com.wshake.service.agent.mcp.McpManageModels.McpReleaseView;
import com.wshake.service.agent.mcp.McpManageModels.McpToolEntry;
import com.wshake.service.agent.mcp.McpManageModels.ProbeResult;
import com.wshake.service.agent.mcp.McpManageModels.ReviewCommand;
import com.wshake.service.agent.mcp.McpManageModels.UpdateMcpDraftCommand;
import com.wshake.service.entity.AgentMcpDraft;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.port.McpProbePort;
import com.wshake.service.repository.AgentMcpDraftRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP 控制面 Service：草稿/验证/审核/Release/市场派生/Binding。
 *
 * <p>密钥规则：市场 MCP 永远无密钥，密钥只落在私有 MCP 与 Agent Binding 上；发布到市场时剥离密钥。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class McpControlService {

    private final AgentMcpDraftRepository draftRepository;
    private final AgentMcpReleaseRepository releaseRepository;
    private final McpProbePort probePort;
    private final Converter converter;

    public PageData<McpDraftView> pageDrafts(McpDraftListQuery query) {
        EasyPageResult<AgentMcpDraft> page = draftRepository.page(
                query.page(), query.pageSize(), query.ownerUserId(), query.name(), query.visibility(), query.status());
        List<AgentMcpDraft> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(rows.stream().map(this::toDraftView).toList(), page.getTotal());
    }

    public McpDraftView getDraft(Long id) {
        return toDraftView(requireDraft(id));
    }

    @Transactional
    public McpDraftView createDraft(CreateMcpDraftCommand cmd) {
        String name = requireName(cmd.name());
        String transport = requireTransport(cmd.transport());
        String url = requireUrl(cmd.url());
        String visibility = requireVisibility(cmd.visibility());
        Long owner = cmd.ownerUserId() == null ? 0L : cmd.ownerUserId();
        if (draftRepository.existsActive(owner, name, visibility, null)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "active mcp draft already exists for same owner/name/visibility");
        }

        AgentMcpDraft row = new AgentMcpDraft();
        row.setName(name);
        row.setTransport(transport);
        row.setUrl(url);
        row.setHeadersJson(AgentJsonSupport.headersToJson(cmd.headers(), "headers"));
        row.setEncryptedSecret(normalizeSecret(cmd.encryptedSecret(), visibility));
        row.setConnectTimeoutMs(normalizeTimeout(cmd.connectTimeoutMs()));
        row.setVisibility(visibility);
        row.setStatus(McpManageModels.STATUS_DRAFT);
        row.setOwnerUserId(owner);
        row.setReviewComment("");
        row.setReviewedBy(0L);
        row.setRemark(McpManageModels.nullToEmpty(cmd.remark()));
        row.setIsEnabled(McpManageModels.normalize01(cmd.isEnabled(), 1));
        draftRepository.insert(row);
        return toDraftView(requireDraft(row.getId()));
    }

    @Transactional
    public McpDraftView updateDraft(UpdateMcpDraftCommand cmd) {
        AgentMcpDraft row = requireDraft(cmd.id());
        if (row.getStatus().equals(McpManageModels.STATUS_CONSUMED)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "consumed draft cannot be updated");
        }
        if (cmd.name() != null) {
            row.setName(requireName(cmd.name()));
        }
        if (cmd.transport() != null) {
            row.setTransport(requireTransport(cmd.transport()));
        }
        if (cmd.url() != null) {
            row.setUrl(requireUrl(cmd.url()));
        }
        if (cmd.headers() != null) {
            row.setHeadersJson(AgentJsonSupport.headersToJson(cmd.headers(), "headers"));
        }
        if (cmd.encryptedSecret() != null) {
            row.setEncryptedSecret(normalizeSecret(cmd.encryptedSecret(), row.getVisibility()));
        }
        if (cmd.connectTimeoutMs() != null) {
            row.setConnectTimeoutMs(normalizeTimeout(cmd.connectTimeoutMs()));
        }
        if (cmd.visibility() != null) {
            String visibility = requireVisibility(cmd.visibility());
            row.setVisibility(visibility);
            // 切换可见性时重新约束密钥：MARKET 无密钥
            if (McpManageModels.VISIBILITY_MARKET.equals(visibility)) {
                row.setEncryptedSecret(null);
            }
        }
        if (cmd.ownerUserId() != null) {
            row.setOwnerUserId(cmd.ownerUserId());
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        if (cmd.isEnabled() != null) {
            row.setIsEnabled(McpManageModels.normalize01(cmd.isEnabled(), 1));
        }
        draftRepository.update(row);
        return toDraftView(requireDraft(row.getId()));
    }

    @Transactional
    public McpDraftView softDelete(Long id) {
        AgentMcpDraft row = requireDraft(id);
        McpDraftView snapshot = toDraftView(row);
        long n = draftRepository.softDeleteById(id);
        if (n == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "mcp draft " + id + " not found");
        }
        return snapshot;
    }

    /** 握手验证草稿连接并返回工具目录，不改变状态。 */
    public ProbeResult verify(Long id) {
        AgentMcpDraft draft = requireDraft(id);
        return doProbe(draft);
    }

    @Transactional
    public McpDraftView submit(Long id) {
        AgentMcpDraft row = requireDraft(id);
        if (!row.getStatus().equals(McpManageModels.STATUS_DRAFT)
                && !row.getStatus().equals(McpManageModels.STATUS_REJECTED)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only DRAFT/REJECTED can be submitted");
        }
        row.setStatus(McpManageModels.STATUS_PENDING_REVIEW);
        draftRepository.update(row);
        return toDraftView(requireDraft(id));
    }

    @Transactional
    public McpReleaseView review(Long id, ReviewCommand cmd) {
        AgentMcpDraft draft = requireDraft(id);
        if (!draft.getStatus().equals(McpManageModels.STATUS_PENDING_REVIEW)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only PENDING_REVIEW can be reviewed");
        }
        String action = cmd.action() == null ? "" : cmd.action().trim().toLowerCase(Locale.ROOT);
        if ("reject".equals(action)) {
            draft.setStatus(McpManageModels.STATUS_REJECTED);
            draft.setReviewComment(McpManageModels.nullToEmpty(cmd.comment()));
            draftRepository.update(draft);
            throw BizException.of(ResultCode.PARAM_INVALID, "draft rejected: " + draft.getReviewComment());
        }
        if (!"approve".equals(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be approve|reject");
        }

        // 再次握手冻结目录（工具目录不落库，仅验证可连通）
        doProbe(draft);

        int version = releaseRepository.maxVersion(draft.getOwnerUserId(), draft.getVisibility(), draft.getName()) + 1;
        AgentMcpRelease release = new AgentMcpRelease();
        release.setOwnerUserId(draft.getOwnerUserId());
        release.setName(draft.getName());
        release.setVisibility(draft.getVisibility());
        release.setVersion(version);
        release.setStatus(McpManageModels.RELEASE_PUBLISHED);
        release.setSourceDraftId(draft.getId());
        release.setTransport(draft.getTransport());
        release.setUrl(draft.getUrl());
        release.setHeadersJson(draft.getHeadersJson());
        // 市场 MCP 无密钥；私有 MCP 带密钥
        release.setEncryptedSecret(
                McpManageModels.VISIBILITY_MARKET.equals(draft.getVisibility()) ? null : draft.getEncryptedSecret());
        release.setConnectTimeoutMs(draft.getConnectTimeoutMs());
        release.setRemark(draft.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);

        draft.setStatus(McpManageModels.STATUS_CONSUMED);
        draft.setReviewComment(McpManageModels.nullToEmpty(cmd.comment()));
        draftRepository.update(draft);

        return toReleaseView(requireRelease(release.getId()));
    }

    public PageData<McpReleaseView> pageReleases(
            int page, int pageSize, Long ownerUserId, String name, String visibility, String status) {
        EasyPageResult<AgentMcpRelease> result =
                releaseRepository.page(page, pageSize, ownerUserId, name, visibility, status);
        List<AgentMcpRelease> rows = result.getData() == null ? List.of() : result.getData();
        return PageData.of(rows.stream().map(this::toReleaseView).toList(), result.getTotal());
    }

    public McpReleaseView getRelease(Long id) {
        return toReleaseView(requireRelease(id));
    }

    public List<McpReleaseView> market() {
        List<AgentMcpRelease> all = releaseRepository.listMarket();
        Map<String, AgentMcpRelease> latest = new java.util.LinkedHashMap<>();
        for (AgentMcpRelease r : all) {
            AgentMcpRelease cur = latest.get(r.getName());
            if (cur == null || r.getVersion() > cur.getVersion()) {
                latest.put(r.getName(), r);
            }
        }
        return new ArrayList<>(latest.values())
                .stream().map(this::toReleaseView).toList();
    }

    @Transactional
    public McpReleaseView deprecate(Long id) {
        AgentMcpRelease release = requireRelease(id);
        releaseRepository.updateStatus(id, McpManageModels.RELEASE_DEPRECATED);
        return toReleaseView(requireRelease(id));
    }

    // ---------- helpers ----------

    private ProbeResult doProbe(AgentMcpDraft draft) {
        List<McpToolEntry> tools = probePort.probe(
                draft.getTransport(),
                draft.getUrl(),
                AgentJsonSupport.parseStringMap(draft.getHeadersJson(), "headers"),
                draft.getConnectTimeoutMs());
        if (tools == null || tools.isEmpty()) {
            throw BizException.of(ResultCode.REMOTE_CALL_FAILED, "mcp probe returned empty tool catalog");
        }
        return new ProbeResult(tools);
    }

    private McpDraftView toDraftView(AgentMcpDraft d) {
        return new McpDraftView(
                d.getId(),
                d.getName(),
                d.getTransport(),
                d.getUrl(),
                AgentJsonSupport.parseStringMap(d.getHeadersJson(), "headers"),
                d.getEncryptedSecret(),
                d.getConnectTimeoutMs(),
                d.getVisibility(),
                d.getStatus(),
                d.getOwnerUserId(),
                d.getReviewComment(),
                d.getReviewedBy(),
                d.getReviewedAt(),
                d.getRemark(),
                d.getIsEnabled(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    private McpReleaseView toReleaseView(AgentMcpRelease r) {
        return new McpReleaseView(
                r.getId(),
                r.getOwnerUserId(),
                r.getName(),
                r.getVisibility(),
                r.getVersion(),
                r.getStatus(),
                r.getSourceDraftId(),
                r.getTransport(),
                r.getUrl(),
                AgentJsonSupport.parseStringMap(r.getHeadersJson(), "headers"),
                r.getEncryptedSecret(),
                r.getConnectTimeoutMs(),
                r.getRemark(),
                r.getIsEnabled(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }

    private AgentMcpDraft requireDraft(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentMcpDraft row = draftRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "mcp draft " + id + " not found");
        }
        return row;
    }

    private AgentMcpRelease requireRelease(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentMcpRelease row = releaseRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "mcp release " + id + " not found");
        }
        return row;
    }

    private static String normalizeSecret(String secret, String visibility) {
        if (McpManageModels.VISIBILITY_MARKET.equals(visibility)) {
            return null;
        }
        return secret == null || secret.isEmpty() ? null : secret;
    }

    private static int normalizeTimeout(Integer timeoutMs) {
        if (timeoutMs == null) {
            return 5000;
        }
        if (timeoutMs <= 0 || timeoutMs > 60000) {
            throw BizException.of(ResultCode.PARAM_INVALID, "connectTimeoutMs must be 1..60000");
        }
        return timeoutMs;
    }

    private static String requireName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        String name = raw.trim();
        if (name.length() > 64) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
        }
        return name;
    }

    private static String requireTransport(String raw) {
        String t = McpManageModels.normalizeEnum(raw);
        if (t == null || (!t.equals("SSE") && !t.equals("HTTP"))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "transport must be sse|http");
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private static String requireUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url is required");
        }
        String url = raw.trim();
        if (url.length() > 512) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url must be ≤ 512 chars");
        }
        return url;
    }

    private static String requireVisibility(String raw) {
        String v = McpManageModels.normalizeEnum(raw);
        if (v == null
                || (!v.equals(McpManageModels.VISIBILITY_MARKET) && !v.equals(McpManageModels.VISIBILITY_PRIVATE))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "visibility must be MARKET|PRIVATE");
        }
        return v;
    }
}
