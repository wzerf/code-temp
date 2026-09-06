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
        List<McpDraftView> views = new ArrayList<>();
        for (AgentMcpDraft row : rows) {
            views.add(toDraftView(row));
        }
        return PageData.of(views, page.getTotal());
    }

    public McpDraftView getDraft(Long id) {
        return toDraftView(requireDraft(id));
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
        applyOauthConfig(
                row,
                visibility,
                cmd.authType(),
                cmd.oauthClientId(),
                cmd.plainOauthClientSecret(),
                cmd.oauthScope(),
                cmd.oauthRequireLogin());
        row.setConnectTimeoutMs(cmd.connectTimeoutMs() == null ? 5000 : Math.max(1, cmd.connectTimeoutMs()));
        row.setRemark(clip(cmd.remark(), 512));
        row.setIsEnabled(1);
        draftRepository.insert(row);
        return toDraftView(requireDraft(row.getId()));
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
        applyOauthConfig(
                row,
                row.getVisibility(),
                cmd.authType(),
                cmd.oauthClientId(),
                cmd.plainOauthClientSecret(),
                cmd.oauthScope(),
                cmd.oauthRequireLogin());
        if (cmd.connectTimeoutMs() != null) {
            row.setConnectTimeoutMs(Math.max(1, cmd.connectTimeoutMs()));
        }
        if (cmd.remark() != null) {
            row.setRemark(clip(cmd.remark(), 512));
        }
        draftRepository.update(row);
        return toDraftView(requireDraft(id));
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

    /** 握手验证：连接草稿并返回工具目录,不改变状态。失败即抛错（未知即拒绝）。需 OAuth 时返回授权地址。 */
    public McpVerifyResult verify(Long id) {
        return verify(id, null);
    }

    /**
     * 握手验证（带用户 token）：OAuth 草稿若调用方已登录，先用用户 access_token 探测；
     * 成功则返回工具目录（登录有效），失败/无 token 则回落发现流程返回授权地址。
     *
     * @param oauthAccessToken 调用方用户 token 明文（可空；只在内存）
     */
    public McpVerifyResult verify(Long id, String oauthAccessToken) {
        AgentMcpDraft row = requireDraft(id);
        // OAuth 草稿：优先用用户 token 直连验证登录有效性
        if (isOauth(row) && oauthAccessToken != null && !oauthAccessToken.isBlank()) {
            try {
                McpProbePort.ProbeResult authed = probeWithToken(row, oauthAccessToken);
                if (!authed.oauthRequired()) {
                    List<McpProbePort.McpToolEntry> tools = authed.tools();
                    if (tools != null && !tools.isEmpty()) {
                        return McpVerifyResult.success(tools);
                    }
                }
            } catch (Exception e) {
                // token 无效则回落发现流程，不抛错
            }
        }
        McpProbePort.ProbeResult result = probe(row);
        if (result.oauthRequired()) {
            cacheDiscovery(row, result.oauth());
            return McpVerifyResult.oauthRequired(result.oauth());
        }
        List<McpProbePort.McpToolEntry> tools = result.tools();
        if (tools == null || tools.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "MCP 工具目录为空,拒绝通过");
        }
        return McpVerifyResult.success(tools);
    }

    /**
     * 通过审核：再次握手冻结连接配置副本,插入不可变 Release,草稿置 CONSUMED。
     *
     * @param publisherUserId 发布者（审核人）用户 id；OAuth 草稿要求校验时用其 token 做登录校验
     * @param publisherAccessToken 发布者 token 明文（可空；只在内存）
     */
    @Transactional
    public McpReleaseView approve(Long id, Long publisherUserId, String publisherAccessToken) {
        AgentMcpDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        McpProbePort.ProbeResult probed = null;
        boolean oauthFirstPublish = false;
        if (isOauth(row)) {
            // OAuth 草稿：用发布者 token 校验登录态
            if (publisherAccessToken != null && !publisherAccessToken.isBlank()) {
                try {
                    McpProbePort.ProbeResult authed = probeWithToken(row, publisherAccessToken);
                    if (!authed.oauthRequired()
                            && authed.tools() != null
                            && !authed.tools().isEmpty()) {
                        probed = authed;
                    }
                } catch (Exception e) {
                    probed = null;
                }
            }
            if (probed == null) {
                // 未登录：要求校验（默认）→ 有旧版则拒绝（去旧版登录），首次发布则放行（发布后登录）；
                // 可选跳过（requireLogin=0）→ 直接放行
                boolean requireLogin = row.getOauthRequireLogin() == null || row.getOauthRequireLogin() != 0;
                if (requireLogin) {
                    AgentMcpRelease previous = releaseRepository.findLatestActive(
                            row.getOwnerUserId(), row.getVisibility(), row.getName());
                    if (previous == null) {
                        // 首次发布：无旧版可登录，放行（先发现缓存端点，冻结配置，发布后在 Release 上登录）
                        try {
                            McpProbePort.ProbeResult discovered = probe(row);
                            if (discovered.oauthRequired()) {
                                cacheDiscovery(row, discovered.oauth());
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                        oauthFirstPublish = true;
                    } else {
                        String endpoint = row.getOauthAuthorizationEndpoint();
                        if (endpoint == null || endpoint.isBlank()) {
                            try {
                                McpProbePort.ProbeResult discovered = probe(row);
                                if (discovered.oauthRequired()) {
                                    cacheDiscovery(row, discovered.oauth());
                                    endpoint = discovered.oauth().authorizationEndpoint();
                                }
                            } catch (Exception e) {
                                // 忽略
                            }
                        }
                        throw BizException.of(
                                ResultCode.PARAM_INVALID,
                                "该 MCP 需要 OAuth 登录后才能发布"
                                        + (endpoint == null || endpoint.isBlank() ? "" : ": " + endpoint));
                    }
                }
            }
        }
        if (probed == null && !oauthFirstPublish) {
            // 握手验证连接可用 + 目录非空；OAuth 未完成不得发布
            probed = probe(row);
            if (probed.oauthRequired()) {
                cacheDiscovery(row, probed.oauth());
                boolean requireLogin = row.getOauthRequireLogin() == null || row.getOauthRequireLogin() != 0;
                if (requireLogin) {
                    AgentMcpRelease previous = releaseRepository.findLatestActive(
                            row.getOwnerUserId(), row.getVisibility(), row.getName());
                    if (previous != null) {
                        throw BizException.of(
                                ResultCode.PARAM_INVALID,
                                "需要 OAuth 登录，无法发布: " + probed.oauth().authorizationEndpoint());
                    }
                    // 首次发布：放行（冻结发现端点，发布后登录）
                    oauthFirstPublish = true;
                }
                // 可选跳过：OAuth Release 冻结发现端点，登录留给使用方
            }
        }
        List<McpProbePort.McpToolEntry> tools = probed == null ? List.of() : probed.tools();
        if (tools == null || tools.isEmpty()) {
            if (isOAuthReleasePending(row) || oauthFirstPublish) {
                // OAuth 可选跳过 / 首次发布未登录：允许空目录发布（工具目录使用时再拉）
                tools = List.of();
            } else {
                throw BizException.of(ResultCode.PARAM_INVALID, "MCP 工具目录为空,拒绝发布");
            }
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
        freezeOauthConfig(release, row);
        release.setConnectTimeoutMs(row.getConnectTimeoutMs());
        release.setSourceDraftId(id);
        release.setRemark(row.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);

        draftRepository.updateStatus(id, STATUS_CONSUMED, "", null, null);
        return getRelease(release.getId());
    }

    /** 兼容旧调用：无发布者登录上下文（OAuth 可选跳过仍可发布；要求校验的 MARKET OAuth 会拒绝）。 */
    @Transactional
    public McpReleaseView approve(Long id) {
        return approve(id, null, null);
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

    private static boolean isOauth(AgentMcpDraft row) {
        return row != null && "OAUTH".equalsIgnoreCase(row.getAuthType());
    }

    /** OAuth 可选跳过（requireLogin=0）且尚未登录时，允许空目录发布（工具目录使用时再拉）。 */
    private static boolean isOAuthReleasePending(AgentMcpDraft row) {
        if (!isOauth(row)) {
            return false;
        }
        return row.getOauthRequireLogin() != null && row.getOauthRequireLogin() == 0;
    }

    /** OAuth 配置落库：NONE 清空 OAuth 列；OAUTH 校验并加密 client_secret。 */
    private void applyOauthConfig(
            AgentMcpDraft row,
            String visibility,
            String authType,
            String oauthClientId,
            String plainOauthClientSecret,
            String oauthScope,
            Integer oauthRequireLogin) {
        boolean oauth = "OAUTH".equalsIgnoreCase(authType);
        // 全空（创建/更新均未传 OAuth 字段）→ 保持原值（更新场景）或默认 NONE（创建场景由 DB 默认）
        if (!oauth
                && isBlank(oauthClientId)
                && isBlank(plainOauthClientSecret)
                && isBlank(oauthScope)
                && oauthRequireLogin == null
                && !"OAUTH".equalsIgnoreCase(row.getAuthType())) {
            if (row.getAuthType() == null || row.getAuthType().isBlank()) {
                row.setAuthType("NONE");
            }
            return;
        }
        if (!oauth) {
            row.setAuthType("NONE");
            row.setOauthClientId("");
            row.setOauthClientSecretEnc(null);
            row.setOauthScope("");
            row.setOauthAuthorizationEndpoint("");
            row.setOauthTokenEndpoint("");
            return;
        }
        row.setAuthType("OAUTH");
        row.setOauthClientId(oauthClientId == null ? "" : oauthClientId.trim());
        if (plainOauthClientSecret != null) {
            if (VIS_MARKET.equals(visibility) && !plainOauthClientSecret.isBlank()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "MARKET 草稿禁止配置 client_secret，请用 DCR 或公开 client");
            }
            row.setOauthClientSecretEnc(secretCipher.encrypt(plainOauthClientSecret));
        }
        if (oauthScope != null) {
            row.setOauthScope(clip(oauthScope.trim(), 1024));
        }
        row.setOauthRequireLogin(oauthRequireLogin == null ? 1 : (oauthRequireLogin == 0 ? 0 : 1));
    }

    /** Release 冻结 OAuth 配置（MARKET 禁止冻结 client_secret）。 */
    private void freezeOauthConfig(AgentMcpRelease release, AgentMcpDraft row) {
        if (!isOauth(row)) {
            release.setAuthType("NONE");
            release.setOauthClientId("");
            release.setOauthClientSecretEnc(null);
            release.setOauthScope("");
            release.setOauthAuthorizationEndpoint("");
            release.setOauthTokenEndpoint("");
            release.setOauthRequireLogin(1);
            return;
        }
        release.setAuthType("OAUTH");
        release.setOauthClientId(row.getOauthClientId() == null ? "" : row.getOauthClientId());
        if (VIS_MARKET.equals(row.getVisibility())) {
            release.setOauthClientSecretEnc(null);
        } else {
            release.setOauthClientSecretEnc(row.getOauthClientSecretEnc());
        }
        release.setOauthScope(row.getOauthScope() == null ? "" : row.getOauthScope());
        release.setOauthAuthorizationEndpoint(
                row.getOauthAuthorizationEndpoint() == null ? "" : row.getOauthAuthorizationEndpoint());
        release.setOauthTokenEndpoint(row.getOauthTokenEndpoint() == null ? "" : row.getOauthTokenEndpoint());
        release.setOauthRequireLogin(row.getOauthRequireLogin() == null ? 1 : row.getOauthRequireLogin());
    }

    /** verify/approve 时把发现到的端点缓存到草稿（下次 start 登录直接用）。 */
    private void cacheDiscovery(AgentMcpDraft row, McpProbePort.OAuthChallenge oauth) {
        if (oauth == null) {
            return;
        }
        boolean changed = false;
        if (oauth.authorizationEndpoint() != null
                && !oauth.authorizationEndpoint().isBlank()
                && !oauth.authorizationEndpoint().equals(row.getOauthAuthorizationEndpoint())) {
            row.setOauthAuthorizationEndpoint(oauth.authorizationEndpoint());
            changed = true;
        }
        if (oauth.scope() != null
                && !oauth.scope().isBlank()
                && (row.getOauthScope() == null || row.getOauthScope().isBlank())) {
            row.setOauthScope(clip(oauth.scope(), 1024));
            changed = true;
        }
        if (changed) {
            draftRepository.update(row);
        }
    }

    private McpProbePort.ProbeResult probeWithToken(AgentMcpDraft draft, String accessToken) {
        Map<String, String> headers = parseHeaders(draft.getHeadersJson());
        headers.put("Authorization", "Bearer " + accessToken);
        try {
            return mcpProbePort.probe(new McpProbePort.ProbeCommand(
                    draft.getTransport(), draft.getUrl(), headers, draft.getConnectTimeoutMs()));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "MCP 握手失败: " + e.getMessage());
        }
    }

    private McpProbePort.ProbeResult probe(AgentMcpDraft draft) {
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
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy(),
                row.getAuthType() == null || row.getAuthType().isBlank() ? "NONE" : row.getAuthType(),
                row.getOauthClientId() == null ? "" : row.getOauthClientId(),
                row.getOauthScope() == null ? "" : row.getOauthScope(),
                row.getOauthAuthorizationEndpoint() == null ? "" : row.getOauthAuthorizationEndpoint(),
                row.getOauthTokenEndpoint() == null ? "" : row.getOauthTokenEndpoint(),
                row.getOauthClientSecretEnc() != null
                        && !row.getOauthClientSecretEnc().isEmpty());
    }

    /** 草稿详情：手动组装 OAuth 字段（AutoMapper 只管基础字段）。 */
    private McpDraftView toDraftView(AgentMcpDraft row) {
        McpDraftView base = converter.convert(row, McpDraftView.class);
        return new McpDraftView(
                base.id(),
                base.ownerUserId(),
                base.name(),
                base.visibility(),
                base.status(),
                base.transport(),
                base.url(),
                base.headersJson(),
                base.connectTimeoutMs(),
                base.remark(),
                base.isEnabled(),
                base.deletedAt(),
                base.createdAt(),
                base.updatedAt(),
                base.createdBy(),
                base.updatedBy(),
                row.getAuthType() == null || row.getAuthType().isBlank() ? "NONE" : row.getAuthType(),
                row.getOauthClientId() == null ? "" : row.getOauthClientId(),
                row.getOauthScope() == null ? "" : row.getOauthScope(),
                row.getOauthAuthorizationEndpoint() == null ? "" : row.getOauthAuthorizationEndpoint(),
                row.getOauthTokenEndpoint() == null ? "" : row.getOauthTokenEndpoint(),
                row.getOauthRequireLogin() == null ? 1 : row.getOauthRequireLogin(),
                row.getOauthClientSecretEnc() != null
                        && !row.getOauthClientSecretEnc().isEmpty());
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
            Long ownerUserId,
            String authType,
            String oauthClientId,
            String plainOauthClientSecret,
            String oauthScope,
            Integer oauthRequireLogin) {
        public CreateMcpCommand(
                String name,
                String transport,
                String url,
                String headersJson,
                String visibility,
                String plainSecret,
                Integer connectTimeoutMs,
                String remark,
                Long ownerUserId) {
            this(
                    name,
                    transport,
                    url,
                    headersJson,
                    visibility,
                    plainSecret,
                    connectTimeoutMs,
                    remark,
                    ownerUserId,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record UpdateMcpCommand(
            String name,
            String transport,
            String url,
            String headersJson,
            String plainSecret,
            Integer connectTimeoutMs,
            String remark,
            String authType,
            String oauthClientId,
            String plainOauthClientSecret,
            String oauthScope,
            Integer oauthRequireLogin) {
        public UpdateMcpCommand(
                String name,
                String transport,
                String url,
                String headersJson,
                String plainSecret,
                Integer connectTimeoutMs,
                String remark) {
            this(
                    name,
                    transport,
                    url,
                    headersJson,
                    plainSecret,
                    connectTimeoutMs,
                    remark,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record McpVerifyResult(
            boolean success,
            String message,
            int toolCount,
            List<McpProbePort.McpToolEntry> tools,
            String oauthAuthorizationUrl,
            String oauthScope,
            String oauthResource,
            String oauthResourceMetadataUrl) {
        static McpVerifyResult success(List<McpProbePort.McpToolEntry> tools) {
            List<McpProbePort.McpToolEntry> list = tools == null ? List.of() : tools;
            return new McpVerifyResult(true, "握手成功", list.size(), list, null, null, null, null);
        }

        static McpVerifyResult oauthRequired(McpProbePort.OAuthChallenge oauth) {
            return new McpVerifyResult(
                    false,
                    "需要 OAuth 登录",
                    0,
                    List.of(),
                    oauth.authorizationEndpoint(),
                    oauth.scope(),
                    oauth.resource(),
                    oauth.resourceMetadataUrl());
        }
    }

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
            Long updatedBy,
            String authType,
            String oauthClientId,
            String oauthScope,
            String oauthAuthorizationEndpoint,
            String oauthTokenEndpoint,
            Integer oauthRequireLogin,
            boolean hasOauthClientSecret) {
        public McpDraftView(
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
            this(
                    id,
                    ownerUserId,
                    name,
                    visibility,
                    status,
                    transport,
                    url,
                    headersJson,
                    connectTimeoutMs,
                    remark,
                    isEnabled,
                    deletedAt,
                    createdAt,
                    updatedAt,
                    createdBy,
                    updatedBy,
                    "NONE",
                    "",
                    "",
                    "",
                    "",
                    1,
                    false);
        }

        public McpDraftView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            headersJson = headersJson == null ? "" : headersJson;
            connectTimeoutMs = connectTimeoutMs == null ? 5000 : connectTimeoutMs;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
            authType = authType == null || authType.isBlank() ? "NONE" : authType;
            oauthClientId = oauthClientId == null ? "" : oauthClientId;
            oauthScope = oauthScope == null ? "" : oauthScope;
            oauthAuthorizationEndpoint = oauthAuthorizationEndpoint == null ? "" : oauthAuthorizationEndpoint;
            oauthTokenEndpoint = oauthTokenEndpoint == null ? "" : oauthTokenEndpoint;
            oauthRequireLogin = oauthRequireLogin == null ? 1 : oauthRequireLogin;
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
            Long updatedBy,
            String authType,
            String oauthClientId,
            String oauthScope,
            String oauthAuthorizationEndpoint,
            String oauthTokenEndpoint,
            boolean hasOauthClientSecret) {}

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String upperToNull(String value) {
        String v = trimToNull(value);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}
