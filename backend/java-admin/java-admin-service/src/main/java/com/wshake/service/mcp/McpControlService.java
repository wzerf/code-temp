package com.wshake.service.mcp;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.constant.PageLimits;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentMcpDraft;
import com.wshake.service.entity.AgentMcpRelease;
import com.wshake.service.port.McpProbePort;
import com.wshake.service.repository.AgentMcpDraftRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MCP 控制面服务：草稿 → verify(握手) → 审核 → 不可变 Release。
 *
 * <p>密钥规则见架构文档 7.3：MARKET 永远无密钥,PRIVATE 自带密钥；发布到市场剥离密钥。
 * 明文密钥只在本服务内存短暂存在,落库一律 {@code encrypted_secret} 密文。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class McpControlService {

    private static final String VIS_MARKET = "MARKET";
    private static final String VIS_PRIVATE = "PRIVATE";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONSUMED = "CONSUMED";

    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_DEPRECATED = "DEPRECATED";

    public static final String TRANSPORT_SSE = "sse";
    public static final String TRANSPORT_HTTP = "http";

    private final AgentMcpDraftRepository draftRepository;
    private final AgentMcpReleaseRepository releaseRepository;
    private final AgentSecretCipher secretCipher;
    private final McpProbePort mcpProbePort;
    private final Converter converter;
    private final ObjectMapper objectMapper;

    // ---------- 草稿 ----------

    public PageData<McpDraftView> pageDraft(McpListQuery q) {
        EasyPageResult<AgentMcpDraft> page =
                draftRepository.page(q.page(), q.pageSize(), q.ownerUserId(), q.nameLike(), q.visibility(), q.status());
        List<AgentMcpDraft> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        return PageData.of(converter.convert(rows, McpDraftView.class), page.getTotal());
    }

    public McpDraftView getDraft(Long id) {
        return converter.convert(requireDraft(id), McpDraftView.class);
    }

    @Transactional
    public McpDraftView createDraft(CreateMcpCommand cmd) {
        String name = requireName(cmd.name());
        String visibility = requireVisibility(cmd.visibility());
        Long owner = cmd.ownerUserId() == null || cmd.ownerUserId() <= 0 ? 0L : cmd.ownerUserId();
        if (draftRepository.existsActiveDraft(owner, name, visibility, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/visibility 已存在活跃草稿");
        }
        AgentMcpDraft row = new AgentMcpDraft();
        row.setOwnerUserId(owner);
        row.setName(name);
        row.setVisibility(visibility);
        row.setStatus(STATUS_DRAFT);
        row.setTransport(requireTransport(cmd.transport()));
        row.setUrl(requireUrl(cmd.url()));
        row.setHeadersJson(normalizeHeaders(cmd.headersJson()));
        // MARKET 草稿剥离明文密钥;PRIVATE 保存加密密文
        String secret = VIS_MARKET.equals(visibility) ? "" : cmd.plainSecret();
        row.setEncryptedSecret(secretCipher.encrypt(secret));
        row.setConnectTimeoutMs(cmd.connectTimeoutMs() == null ? 5000 : Math.max(1, cmd.connectTimeoutMs()));
        row.setRemark(clip(cmd.remark(), 512));
        row.setIsEnabled(1);
        draftRepository.insert(row);
        return converter.convert(requireDraft(row.getId()), McpDraftView.class);
    }

    @Transactional
    public McpDraftView updateDraft(Long id, UpdateMcpCommand cmd) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_DRAFT, STATUS_REJECTED);
        if (cmd.name() != null) {
            String name = requireName(cmd.name());
            if (!name.equals(row.getName())
                    && draftRepository.existsActiveDraft(row.getOwnerUserId(), name, row.getVisibility(), id)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/visibility 已存在活跃草稿");
            }
            row.setName(name);
        }
        if (cmd.transport() != null) {
            row.setTransport(requireTransport(cmd.transport()));
        }
        if (cmd.url() != null) {
            row.setUrl(requireUrl(cmd.url()));
        }
        if (cmd.headersJson() != null) {
            row.setHeadersJson(normalizeHeaders(cmd.headersJson()));
        }
        if (cmd.plainSecret() != null) {
            String secret = VIS_MARKET.equals(row.getVisibility()) ? "" : cmd.plainSecret();
            row.setEncryptedSecret(secretCipher.encrypt(secret));
        }
        if (cmd.connectTimeoutMs() != null) {
            row.setConnectTimeoutMs(Math.max(1, cmd.connectTimeoutMs()));
        }
        if (cmd.remark() != null) {
            row.setRemark(clip(cmd.remark(), 512));
        }
        draftRepository.update(row);
        return converter.convert(requireDraft(id), McpDraftView.class);
    }

    @Transactional
    public void softDeleteDraft(Long id) {
        requireDraft(id);
        draftRepository.softDeleteById(id);
    }

    @Transactional
    public void submit(Long id) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_DRAFT, STATUS_REJECTED);
        draftRepository.updateStatus(id, STATUS_PENDING_REVIEW, "", null, null);
    }

    @Transactional
    public void withdraw(Long id) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_DRAFT, "", null, null);
    }

    @Transactional
    public void reject(Long id, String reason) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_REJECTED, clip(reason, 512), null, null);
    }

    /** 握手验证：连接草稿并返回工具目录,不改变状态。失败即抛错（未知即拒绝）。 */
    public List<McpProbePort.McpToolEntry> verify(Long id) {
        AgentMcpDraft row = requireDraft(id);
        List<McpProbePort.McpToolEntry> tools = probe(row);
        if (tools.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "MCP 工具目录为空,拒绝通过");
        }
        return tools;
    }

    /**
     * 通过审核：再次握手冻结连接配置副本,插入不可变 Release,草稿置 CONSUMED。
     */
    @Transactional
    public McpReleaseView approve(Long id) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        // 握手验证连接可用 + 目录非空
        List<McpProbePort.McpToolEntry> tools = probe(row);
        if (tools.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "MCP 工具目录为空,拒绝发布");
        }

        int nextVersion = nextVersion(row.getOwnerUserId(), row.getVisibility(), row.getName());
        AgentMcpRelease release = new AgentMcpRelease();
        release.setOwnerUserId(row.getOwnerUserId());
        release.setName(row.getName());
        release.setVisibility(row.getVisibility());
        release.setStatus(RELEASE_PUBLISHED);
        release.setVersion(nextVersion);
        release.setTransport(row.getTransport());
        release.setUrl(row.getUrl());
        release.setHeadersJson(jsonOrNull(row.getHeadersJson()));
        // MARKET 发布剥离密钥:release 不落密钥;PRIVATE 沿用草稿密文
        release.setEncryptedSecret(VIS_MARKET.equals(row.getVisibility()) ? null : row.getEncryptedSecret());
        release.setConnectTimeoutMs(row.getConnectTimeoutMs());
        release.setSourceDraftId(id);
        release.setRemark(row.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);

        draftRepository.updateStatus(id, STATUS_CONSUMED, "", null, null);
        return getRelease(release.getId());
    }

    // ---------- Release / 市场 ----------

    public PageData<McpReleaseView> pageRelease(ReleaseListQuery q) {
        EasyPageResult<AgentMcpRelease> page =
                releaseRepository.page(q.page(), q.pageSize(), q.visibility(), q.status(), q.nameLike());
        List<AgentMcpRelease> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        List<McpReleaseView> views = new ArrayList<>();
        for (AgentMcpRelease row : rows) {
            views.add(toReleaseView(row));
        }
        return PageData.of(views, page.getTotal());
    }

    public McpReleaseView getRelease(Long id) {
        return toReleaseView(requireRelease(id));
    }

    /** 市场列表 = MARKET + PUBLISHED,按 name 取 version 最大一条。 */
    public List<McpReleaseView> listMarket() {
        Map<String, AgentMcpRelease> latest = new LinkedHashMap<>();
        for (AgentMcpRelease row : releaseRepository.listMarket()) {
            AgentMcpRelease existing = latest.get(row.getName());
            if (existing == null || row.getVersion() > existing.getVersion()) {
                latest.put(row.getName(), row);
            }
        }
        List<McpReleaseView> views = new ArrayList<>();
        for (AgentMcpRelease row : latest.values()) {
            views.add(toReleaseView(row));
        }
        return views;
    }

    /** 可绑定候选（MARKET 全量 + 本人 PRIVATE 最新 PUBLISHED）。 */
    public List<McpReleaseView> listBindable(Long ownerUserId) {
        List<McpReleaseView> views = new ArrayList<>();
        for (AgentMcpRelease row : releaseRepository.listMarket()) {
            views.add(toReleaseView(row));
        }
        if (ownerUserId != null && ownerUserId > 0) {
            // 私有候选：owner=当前用户 且 PUBLISHED,按 name 取最新
            List<AgentMcpRelease> owned = releaseRepository.listByOwnerForBind(ownerUserId);
            Map<String, AgentMcpRelease> latest = new LinkedHashMap<>();
            for (AgentMcpRelease row : owned) {
                AgentMcpRelease existing = latest.get(row.getName());
                if (existing == null || row.getVersion() > existing.getVersion()) {
                    latest.put(row.getName(), row);
                }
            }
            for (AgentMcpRelease row : latest.values()) {
                views.add(toReleaseView(row));
            }
        }
        return views;
    }

    @Transactional
    public void deprecate(Long id) {
        requireRelease(id);
        releaseRepository.updateStatus(id, RELEASE_DEPRECATED);
    }

    @Transactional
    public void takeDownMarket(Long id) {
        AgentMcpRelease row = requireRelease(id);
        if (!VIS_MARKET.equals(row.getVisibility()) || !RELEASE_PUBLISHED.equals(row.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "仅 MARKET 且 PUBLISHED 的 Release 可下架");
        }
        releaseRepository.updateStatus(id, RELEASE_DEPRECATED);
    }

    // ---------- 内部 ----------

    private List<McpProbePort.McpToolEntry> probe(AgentMcpDraft draft) {
        // 组装头: headers_json(静态) + 解密密钥注入 Authorization(若为 Bearer 类密钥)
        Map<String, String> headers = parseHeaders(draft.getHeadersJson());
        String secret = secretCipher.decrypt(draft.getEncryptedSecret());
        if (secret != null && !secret.isEmpty()) {
            headers.putIfAbsent("Authorization", "Bearer " + secret);
        }
        try {
            return mcpProbePort.probe(new McpProbePort.ProbeCommand(
                    draft.getTransport(), draft.getUrl(), headers, draft.getConnectTimeoutMs()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "MCP 握手失败: " + e.getMessage());
        }
    }

    private Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(headersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "headersJson 必须为字符串字典 JSON");
        }
    }

    private String normalizeHeaders(String headersJson) {
        Map<String, String> map = parseHeaders(headersJson);
        if (map.isEmpty()) {
            // MySQL JSON 列禁止空串;无头时存 NULL
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "headersJson 序列化失败");
        }
    }

    /** MySQL JSON 列禁止空串:blank/null → null。 */
    private static String jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private int nextVersion(Long owner, String visibility, String name) {
        List<AgentMcpRelease> history = releaseRepository.listByNameAllVersions(owner, visibility, name);
        int max = 0;
        for (AgentMcpRelease r : history) {
            if (r.getVersion() != null && r.getVersion() > max) {
                max = r.getVersion();
            }
        }
        return max + 1;
    }

    private McpReleaseView toReleaseView(AgentMcpRelease row) {
        return new McpReleaseView(
                row.getId(),
                row.getOwnerUserId(),
                row.getName(),
                row.getVisibility(),
                row.getStatus(),
                row.getVersion(),
                row.getTransport(),
                row.getUrl(),
                row.getHeadersJson(),
                // 不回传密钥密文给前端;仅标记是否有密钥
                row.getEncryptedSecret() != null && !row.getEncryptedSecret().isEmpty(),
                row.getConnectTimeoutMs(),
                row.getSourceDraftId(),
                row.getRemark(),
                row.getIsEnabled(),
                row.getDeletedAt() == null ? 0L : row.getDeletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy() == null ? 0L : row.getCreatedBy(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy());
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

    private static void requireStatus(AgentMcpDraft row, String... allowed) {
        for (String s : allowed) {
            if (s.equals(row.getStatus())) {
                return;
            }
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID, "mcp draft " + row.getId() + " 状态 " + row.getStatus() + " 不允许该操作");
    }

    private static String requireName(String raw) {
        String name = raw == null ? null : raw.trim();
        if (name == null || name.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        if (name.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 128 chars");
        }
        return name;
    }

    private static String requireVisibility(String raw) {
        String v = raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
        if (!VIS_MARKET.equals(v) && !VIS_PRIVATE.equals(v)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "visibility must be MARKET|PRIVATE");
        }
        return v;
    }

    private static String requireTransport(String raw) {
        String t = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (!TRANSPORT_SSE.equals(t) && !TRANSPORT_HTTP.equals(t)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "transport must be sse|http");
        }
        return t;
    }

    private static String requireUrl(String raw) {
        String url = raw == null ? null : raw.trim();
        if (url == null || url.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url is required");
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "url 必须为 http(s) 地址");
        }
        return url;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    // ---------- 领域模型 ----------

    public record McpListQuery(
            int page, int pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
        public static McpListQuery of(
                Integer page, Integer pageSize, Long ownerUserId, String nameLike, String visibility, String status) {
            return new McpListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    ownerUserId,
                    trimToNull(nameLike),
                    upperToNull(visibility),
                    upperToNull(status));
        }
    }

    public record CreateMcpCommand(
            String name,
            String transport,
            String url,
            String headersJson,
            String visibility,
            String plainSecret,
            Integer connectTimeoutMs,
            String remark,
            Long ownerUserId) {}

    public record UpdateMcpCommand(
            String name,
            String transport,
            String url,
            String headersJson,
            String plainSecret,
            Integer connectTimeoutMs,
            String remark) {}

    public record McpVerifyResult(boolean success, String message, int toolCount) {}

    @io.github.linpeilie.annotations.AutoMapper(target = AgentMcpDraft.class)
    public record McpDraftView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            String status,
            String transport,
            String url,
            String headersJson,
            Integer connectTimeoutMs,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public McpDraftView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            headersJson = headersJson == null ? "" : headersJson;
            connectTimeoutMs = connectTimeoutMs == null ? 5000 : connectTimeoutMs;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record ReleaseListQuery(int page, int pageSize, String visibility, String status, String nameLike) {
        public static ReleaseListQuery of(
                Integer page, Integer pageSize, String visibility, String status, String nameLike) {
            return new ReleaseListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    upperToNull(visibility),
                    upperToNull(status),
                    trimToNull(nameLike));
        }
    }

    public record McpReleaseView(
            Long id,
            Long ownerUserId,
            String name,
            String visibility,
            String status,
            Integer version,
            String transport,
            String url,
            String headersJson,
            boolean hasSecret,
            Integer connectTimeoutMs,
            Long sourceDraftId,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {}

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static String upperToNull(String value) {
        String v = trimToNull(value);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
