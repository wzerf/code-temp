package com.wshake.service.model;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.constant.PageLimits;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.entity.AgentModelDraft;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.port.ModelProbePort;
import com.wshake.service.repository.AgentModelDraftRepository;
import com.wshake.service.repository.AgentModelReleaseRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型控制面服务：草稿 → verify(探测) → 审核/免审发布 → 不可变 Release → 可用模型池。
 *
 * <p>官方模型必须走审核；私有模型可从 DRAFT 直接发布，但仍须探测通过。
 * 明文密钥只在本服务内存短暂存在，落库一律 {@code encrypted_secret} 密文。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class ModelControlService {

    public static final String SCOPE_OFFICIAL = "OFFICIAL";
    public static final String SCOPE_PRIVATE = "PRIVATE";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CONSUMED = "CONSUMED";

    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_DEPRECATED = "DEPRECATED";

    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
    public static final String PROVIDER_ANTHROPIC = "anthropic";

    static final String DEFAULT_CAPABILITIES =
            "{\"text\":true,\"thinking\":false,\"tool_use\":true,\"vision\":false,\"json_mode\":true}";
    static final String DEFAULT_GUARDRAILS =
            "{\"temperature\":{\"min\":0,\"max\":2,\"default\":0.7},\"top_p\":{\"min\":0,\"max\":1,\"default\":1},\"max_tokens\":{\"min\":1,\"max\":128000,\"default\":4096}}";
    static final long DEFAULT_CONTEXT_LENGTH = 500_000L;

    private final AgentModelDraftRepository draftRepository;
    private final AgentModelReleaseRepository releaseRepository;
    private final AgentSecretCipher secretCipher;
    private final ModelProbePort modelProbePort;
    private final Converter converter;
    private final ObjectMapper objectMapper;

    // ---------- 草稿 ----------

    public PageData<ModelDraftView> pageDraft(ModelListQuery q) {
        EasyPageResult<AgentModelDraft> page =
                draftRepository.page(q.page(), q.pageSize(), q.ownerUserId(), q.nameLike(), q.scope(), q.status());
        List<AgentModelDraft> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        return PageData.of(converter.convert(rows, ModelDraftView.class), page.getTotal());
    }

    public ModelDraftView getDraft(Long id) {
        return converter.convert(requireDraft(id), ModelDraftView.class);
    }

    @Transactional
    public ModelDraftView createDraft(CreateModelCommand cmd) {
        String name = requireName(cmd.name());
        String scope = requireScope(cmd.scope());
        Long owner = resolveOwner(cmd.ownerUserId(), scope);
        if (draftRepository.existsActiveDraft(owner, name, scope, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/scope 已存在活跃草稿");
        }
        AgentModelDraft row = new AgentModelDraft();
        row.setOwnerUserId(owner);
        row.setName(name);
        row.setScope(scope);
        row.setCode(clip(cmd.code(), 64));
        row.setStatus(STATUS_DRAFT);
        row.setProvider(requireProvider(cmd.provider()));
        row.setBaseUrl(requireHttpsUrl(cmd.baseUrl()));
        row.setModelName(requireModelName(cmd.modelName()));
        row.setCapabilities(normalizeJsonObject(cmd.capabilities(), DEFAULT_CAPABILITIES, "capabilities"));
        row.setParameterGuardrails(
                normalizeJsonObject(cmd.parameterGuardrails(), DEFAULT_GUARDRAILS, "parameterGuardrails"));
        row.setContextLength(normalizeContextLength(cmd.contextLength()));
        row.setEncryptedSecret(secretCipher.encrypt(requireSecret(cmd.plainSecret())));
        row.setRemark(clip(cmd.remark(), 512));
        row.setIsEnabled(1);
        draftRepository.insert(row);
        return converter.convert(requireDraft(row.getId()), ModelDraftView.class);
    }

    /**
     * 同一连接下批量创建草稿。共享 provider/baseUrl/密钥，每条草稿独立 name/modelName。
     */
    @Transactional
    public List<ModelDraftView> createDrafts(BatchCreateModelCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "至少选择一个模型");
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> modelNames = new java.util.LinkedHashSet<>();
        for (ModelDraftItem item : cmd.items()) {
            String name = requireName(item.name() == null || item.name().isBlank() ? item.modelName() : item.name());
            String modelName = requireModelName(item.modelName());
            if (!names.add(name)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "批量创建中显示名重复: " + name);
            }
            if (!modelNames.add(modelName)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "批量创建中 modelName 重复: " + modelName);
            }
        }
        List<ModelDraftView> views = new ArrayList<>();
        for (ModelDraftItem item : cmd.items()) {
            String name = item.name() == null || item.name().isBlank() ? item.modelName() : item.name();
            views.add(createDraft(new CreateModelCommand(
                    name,
                    cmd.scope(),
                    item.code(),
                    cmd.provider(),
                    cmd.baseUrl(),
                    item.modelName(),
                    item.capabilities() != null ? item.capabilities() : cmd.capabilities(),
                    item.parameterGuardrails() != null ? item.parameterGuardrails() : cmd.parameterGuardrails(),
                    item.contextLength(),
                    cmd.plainSecret(),
                    cmd.remark(),
                    cmd.ownerUserId())));
        }
        return views;
    }

    @Transactional
    public ModelDraftView updateDraft(Long id, UpdateModelCommand cmd) {
        AgentModelDraft row = requireDraft(id);
        requireStatus(row, STATUS_DRAFT, STATUS_REJECTED);
        if (cmd.name() != null) {
            String name = requireName(cmd.name());
            if (!name.equals(row.getName())
                    && draftRepository.existsActiveDraft(row.getOwnerUserId(), name, row.getScope(), id)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "同一 owner/name/scope 已存在活跃草稿");
            }
            row.setName(name);
        }
        if (cmd.code() != null) {
            row.setCode(clip(cmd.code(), 64));
        }
        if (cmd.provider() != null) {
            row.setProvider(requireProvider(cmd.provider()));
        }
        if (cmd.baseUrl() != null) {
            row.setBaseUrl(requireHttpsUrl(cmd.baseUrl()));
        }
        if (cmd.modelName() != null) {
            row.setModelName(requireModelName(cmd.modelName()));
        }
        if (cmd.capabilities() != null) {
            row.setCapabilities(normalizeJsonObject(cmd.capabilities(), DEFAULT_CAPABILITIES, "capabilities"));
        }
        if (cmd.parameterGuardrails() != null) {
            row.setParameterGuardrails(
                    normalizeJsonObject(cmd.parameterGuardrails(), DEFAULT_GUARDRAILS, "parameterGuardrails"));
        }
        if (cmd.contextLength() != null) {
            row.setContextLength(normalizeContextLength(cmd.contextLength()));
        }
        if (cmd.plainSecret() != null) {
            row.setEncryptedSecret(secretCipher.encrypt(cmd.plainSecret()));
        }
        if (cmd.remark() != null) {
            row.setRemark(clip(cmd.remark(), 512));
        }
        draftRepository.update(row);
        return converter.convert(requireDraft(id), ModelDraftView.class);
    }

    @Transactional
    public void softDeleteDraft(Long id) {
        requireDraft(id);
        draftRepository.softDeleteById(id);
    }

    @Transactional
    public void submit(Long id) {
        AgentModelDraft row = requireDraft(id);
        requireStatus(row, STATUS_DRAFT, STATUS_REJECTED);
        draftRepository.updateStatus(id, STATUS_PENDING_REVIEW, "", null, null);
    }

    @Transactional
    public void withdraw(Long id) {
        AgentModelDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_DRAFT, "", null, null);
    }

    @Transactional
    public void reject(Long id, String reason) {
        AgentModelDraft row = requireDraft(id);
        requireStatus(row, STATUS_PENDING_REVIEW);
        draftRepository.updateStatus(id, STATUS_REJECTED, clip(reason, 512), null, null);
    }

    /** 探测验证：连接草稿并返回目录摘要，不改变状态。失败即抛错。 */
    public ModelProbePort.ProbeResult verify(Long id) {
        AgentModelDraft row = requireDraft(id);
        return probe(row);
    }

    /**
     * 创建前探测远端目录（不落库、不要求已有草稿）。目录为空即拒绝。
     */
    public ModelProbePort.ProbeResult probeCatalog(String provider, String baseUrl, String plainSecret) {
        String p = requireProvider(provider);
        String url = requireHttpsUrl(baseUrl);
        try {
            ModelProbePort.ProbeResult result =
                    modelProbePort.probe(new ModelProbePort.ProbeCommand(p, url, "", plainSecret));
            if (result == null
                    || result.remoteModelIds() == null
                    || result.remoteModelIds().isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "模型探测失败: 远端目录为空");
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "模型探测失败: " + e.getMessage());
        }
    }

    /**
     * 发布 Release：再次探测冻结连接配置，插入不可变 Release，草稿置 CONSUMED。
     *
     * <p>官方必须处于 PENDING_REVIEW；私有允许 DRAFT / REJECTED / PENDING_REVIEW（免审）。
     */
    @Transactional
    public ModelReleaseView approve(Long id) {
        AgentModelDraft row = requireDraft(id);
        if (SCOPE_OFFICIAL.equals(row.getScope())) {
            requireStatus(row, STATUS_PENDING_REVIEW);
        } else {
            requireStatus(row, STATUS_DRAFT, STATUS_REJECTED, STATUS_PENDING_REVIEW);
        }
        probe(row);

        int nextVersion = nextVersion(row.getOwnerUserId(), row.getScope(), row.getName());
        AgentModelRelease release = new AgentModelRelease();
        release.setOwnerUserId(row.getOwnerUserId());
        release.setName(row.getName());
        release.setScope(row.getScope());
        release.setCode(row.getCode() == null ? "" : row.getCode());
        release.setStatus(RELEASE_PUBLISHED);
        release.setVersion(nextVersion);
        release.setProvider(row.getProvider());
        release.setBaseUrl(row.getBaseUrl());
        release.setModelName(row.getModelName());
        release.setCapabilities(jsonOrNull(row.getCapabilities()));
        release.setParameterGuardrails(jsonOrNull(row.getParameterGuardrails()));
        release.setContextLength(row.getContextLength());
        release.setEncryptedSecret(row.getEncryptedSecret());
        release.setSourceDraftId(id);
        release.setRemark(row.getRemark());
        release.setIsEnabled(1);
        releaseRepository.insert(release);

        draftRepository.updateStatus(id, STATUS_CONSUMED, "", null, null);
        return getRelease(release.getId());
    }

    // ---------- Release / 可用池 ----------

    public PageData<ModelReleaseView> pageRelease(ReleaseListQuery q) {
        EasyPageResult<AgentModelRelease> page =
                releaseRepository.page(q.page(), q.pageSize(), q.scope(), q.status(), q.nameLike());
        List<AgentModelRelease> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        List<ModelReleaseView> views = new ArrayList<>();
        for (AgentModelRelease row : rows) {
            views.add(toReleaseView(row));
        }
        return PageData.of(views, page.getTotal());
    }

    public ModelReleaseView getRelease(Long id) {
        return toReleaseView(requireRelease(id));
    }

    /**
     * 可用模型候选 = 官方 PUBLISHED ∪ 调用者自己的私有 PUBLISHED。
     */
    public List<ModelReleaseView> listAvailable(Long ownerUserId) {
        List<ModelReleaseView> views = new ArrayList<>();
        for (AgentModelRelease row : releaseRepository.listOfficialPublished()) {
            views.add(toReleaseView(row));
        }
        if (ownerUserId != null && ownerUserId > 0) {
            for (AgentModelRelease row : releaseRepository.listPrivatePublishedByOwner(ownerUserId)) {
                views.add(toReleaseView(row));
            }
        }
        return views;
    }

    /** 会话绑定前校验：Release 必须 PUBLISHED，且对调用者可见。 */
    public AgentModelRelease requireUsableRelease(Long releaseId, Long ownerUserId) {
        AgentModelRelease row = requireRelease(releaseId);
        if (!RELEASE_PUBLISHED.equals(row.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "模型 Release " + releaseId + " 不可用");
        }
        if (SCOPE_OFFICIAL.equals(row.getScope())) {
            return row;
        }
        if (ownerUserId == null || !ownerUserId.equals(row.getOwnerUserId())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "无权使用该私有模型");
        }
        return row;
    }

    @Transactional
    public void deprecate(Long id) {
        requireRelease(id);
        releaseRepository.updateStatus(id, RELEASE_DEPRECATED);
    }

    // ---------- 内部 ----------

    private ModelProbePort.ProbeResult probe(AgentModelDraft draft) {
        String secret = requireSecret(secretCipher.decrypt(draft.getEncryptedSecret()));
        try {
            ModelProbePort.ProbeResult result = modelProbePort.probe(new ModelProbePort.ProbeCommand(
                    draft.getProvider(), draft.getBaseUrl(), draft.getModelName(), secret));
            if (result == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "模型探测失败: 空结果");
            }
            if (!result.modelNameMatched()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "模型探测失败: 远端目录未包含 modelName=" + draft.getModelName());
            }
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "模型探测失败: " + e.getMessage());
        }
    }

    private int nextVersion(Long owner, String scope, String name) {
        List<AgentModelRelease> history = releaseRepository.listByNameAllVersions(owner, scope, name);
        int max = 0;
        for (AgentModelRelease r : history) {
            if (r.getVersion() != null && r.getVersion() > max) {
                max = r.getVersion();
            }
        }
        return max + 1;
    }

    private ModelReleaseView toReleaseView(AgentModelRelease row) {
        return new ModelReleaseView(
                row.getId(),
                row.getOwnerUserId(),
                row.getName(),
                row.getScope(),
                row.getCode() == null ? "" : row.getCode(),
                row.getStatus(),
                row.getVersion(),
                row.getProvider(),
                row.getBaseUrl(),
                row.getModelName(),
                row.getCapabilities() == null ? "" : row.getCapabilities(),
                row.getParameterGuardrails() == null ? "" : row.getParameterGuardrails(),
                row.getContextLength(),
                row.getEncryptedSecret() != null && !row.getEncryptedSecret().isEmpty(),
                row.getSourceDraftId(),
                row.getRemark(),
                row.getIsEnabled(),
                row.getDeletedAt() == null ? 0L : row.getDeletedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy() == null ? 0L : row.getCreatedBy(),
                row.getUpdatedBy() == null ? 0L : row.getUpdatedBy());
    }

    private AgentModelDraft requireDraft(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentModelDraft row = draftRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "model draft " + id + " not found");
        }
        return row;
    }

    private AgentModelRelease requireRelease(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentModelRelease row = releaseRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "model release " + id + " not found");
        }
        return row;
    }

    private static void requireStatus(AgentModelDraft row, String... allowed) {
        for (String s : allowed) {
            if (s.equals(row.getStatus())) {
                return;
            }
        }
        throw BizException.of(
                ResultCode.PARAM_INVALID, "model draft " + row.getId() + " 状态 " + row.getStatus() + " 不允许该操作");
    }

    private static Long resolveOwner(Long ownerUserId, String scope) {
        if (SCOPE_PRIVATE.equals(scope)) {
            if (ownerUserId == null || ownerUserId <= 0) {
                throw BizException.of(ResultCode.PARAM_INVALID, "私有模型必须归属当前登录用户");
            }
            return ownerUserId;
        }
        return ownerUserId == null || ownerUserId <= 0 ? 0L : ownerUserId;
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

    private static String requireSecret(String raw) {
        String secret = raw == null ? null : raw.trim();
        if (secret == null || secret.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "请先配置 API Key");
        }
        return secret;
    }

    private static String requireModelName(String raw) {
        String name = raw == null ? null : raw.trim();
        if (name == null || name.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "modelName is required");
        }
        if (name.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "modelName must be ≤ 128 chars");
        }
        return name;
    }

    private static String requireScope(String raw) {
        String v = raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_OFFICIAL.equals(v) && !SCOPE_PRIVATE.equals(v)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "scope must be OFFICIAL|PRIVATE");
        }
        return v;
    }

    private static String requireProvider(String raw) {
        String t = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDER_OPENAI_COMPATIBLE.equals(t) && !PROVIDER_ANTHROPIC.equals(t)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "provider must be openai-compatible|anthropic");
        }
        return t;
    }

    private static String requireHttpsUrl(String raw) {
        String url = raw == null ? null : raw.trim();
        if (url == null || url.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "baseUrl is required");
        }
        if (!url.startsWith("https://")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "baseUrl 必须为 https 地址");
        }
        return url;
    }

    private static long normalizeContextLength(Long value) {
        long contextLength = value == null ? DEFAULT_CONTEXT_LENGTH : value;
        if (contextLength <= 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "contextLength 必须大于 0");
        }
        return contextLength;
    }

    private String normalizeJsonObject(String raw, String fallback, String field) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isObject()) {
                throw BizException.of(ResultCode.PARAM_INVALID, field + " 必须为 JSON 对象");
            }
            return objectMapper.writeValueAsString(node);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " 必须为合法 JSON 对象");
        }
    }

    private static String jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    // ---------- 领域模型 ----------

    public record ModelListQuery(
            int page, int pageSize, Long ownerUserId, String nameLike, String scope, String status) {
        public static ModelListQuery of(
                Integer page, Integer pageSize, Long ownerUserId, String nameLike, String scope, String status) {
            return new ModelListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    ownerUserId,
                    trimToNull(nameLike),
                    upperToNull(scope),
                    upperToNull(status));
        }
    }

    public record CreateModelCommand(
            String name,
            String scope,
            String code,
            String provider,
            String baseUrl,
            String modelName,
            String capabilities,
            String parameterGuardrails,
            Long contextLength,
            String plainSecret,
            String remark,
            Long ownerUserId) {}

    public record ModelDraftItem(
            String name,
            String modelName,
            String code,
            String capabilities,
            String parameterGuardrails,
            Long contextLength) {}

    public record BatchCreateModelCommand(
            String scope,
            String provider,
            String baseUrl,
            String plainSecret,
            String capabilities,
            String parameterGuardrails,
            String remark,
            Long ownerUserId,
            List<ModelDraftItem> items) {}

    public record UpdateModelCommand(
            String name,
            String code,
            String provider,
            String baseUrl,
            String modelName,
            String capabilities,
            String parameterGuardrails,
            Long contextLength,
            String plainSecret,
            String remark) {}

    @io.github.linpeilie.annotations.AutoMapper(target = AgentModelDraft.class)
    public record ModelDraftView(
            Long id,
            Long ownerUserId,
            String name,
            String scope,
            String code,
            String status,
            String provider,
            String baseUrl,
            String modelName,
            String capabilities,
            String parameterGuardrails,
            Long contextLength,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public ModelDraftView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            code = code == null ? "" : code;
            capabilities = capabilities == null ? "" : capabilities;
            parameterGuardrails = parameterGuardrails == null ? "" : parameterGuardrails;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record ReleaseListQuery(int page, int pageSize, String scope, String status, String nameLike) {
        public static ReleaseListQuery of(
                Integer page, Integer pageSize, String scope, String status, String nameLike) {
            return new ReleaseListQuery(
                    PageLimits.page(page),
                    PageLimits.size(pageSize),
                    upperToNull(scope),
                    upperToNull(status),
                    trimToNull(nameLike));
        }
    }

    public record ModelReleaseView(
            Long id,
            Long ownerUserId,
            String name,
            String scope,
            String code,
            String status,
            Integer version,
            String provider,
            String baseUrl,
            String modelName,
            String capabilities,
            String parameterGuardrails,
            Long contextLength,
            boolean hasSecret,
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
