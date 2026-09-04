package com.wshake.infra.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSessionService;
import com.wshake.service.agent.AgentSessionService.AgentSessionView;
import com.wshake.service.entity.AgentModelRelease;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentSession;
import com.wshake.service.entity.AgentSessionModelBinding;
import com.wshake.service.model.ModelControlService;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentSessionModelBindingRepository;
import com.wshake.service.repository.AgentSessionRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent 运行计划装配：会话 → 固定 Revision → 最终模型选择 → 运行快照。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.1/§5.2：首次运行固定当前发布 Revision
 * （复用控制面 {@link AgentSessionService#bindSessionRevision} 的「未绑定才绑定」语义）；
 * 模型选择 = Session 记住的选择（{@code agent_session_model_binding}）优先，
 * 否则回落 Revision {@code model_config.default_model_release_id}；两者皆无则拒绝首启。
 *
 * <p>模型 Release 经 {@link ModelControlService#requireUsableRelease} 校验（PUBLISHED +
 * 可见性）。密钥在内存解密注入运行计划，明文不落库/日志。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentRunPlanner {

    private static final String MODEL_CONFIG_DEFAULT_MODEL_KEY = "default_model_release_id";
    private static final String PERMISSION_ALLOWED_TOOLS_KEY = "allowedTools";
    private static final String DEFAULT_ENDPOINT = "/v1/chat/completions";

    private final AgentSessionService sessionService;
    private final AgentSessionRepository sessionRepository;
    private final AgentSessionModelBindingRepository sessionModelBindingRepository;
    private final AgentRevisionRepository revisionRepository;
    private final ModelControlService modelControlService;
    private final AgentSecretCipher secretCipher;
    private final AgentBindingLoader bindingLoader;
    private final ObjectMapper objectMapper;

    /**
     * 为指定会话装配一次运行计划。
     *
     * @param sessionId     会话 id
     * @param requestUserId 当前登录用户（会话归属校验；null 表示不校验）
     * @return 运行计划
     */
    public AgentRunPlan plan(Long sessionId, Long requestUserId) {
        AgentSession session = requireSession(sessionId);
        checkOwner(session, requestUserId);
        // 首次运行固定 Revision（未绑定才绑定；并发由调用方运行锁串行化）
        AgentSessionView boundView = sessionService.bindSessionRevision(sessionId);
        Long revisionId = boundView.agentRevisionId();
        if (revisionId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "会话未固定 Revision");
        }
        AgentRevision revision = revisionRepository.findById(revisionId);
        if (revision == null || revision.getIsEnabled() == null || revision.getIsEnabled() != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Agent Revision 不可用");
        }
        // 首期策略：记忆/压缩策略非空即拒绝（运行面不实现）
        rejectIfNonBlank(revision.getMemoryPolicy(), "Agent Revision 配置了 memory_policy,运行面暂不支持");
        rejectIfNonBlank(revision.getCompressionPolicy(), "Agent Revision 配置了 compression_policy,运行面暂不支持");

        // 最终模型 Release：Session 记住的选择 > Revision model_config 默认
        Long modelReleaseId = resolveSessionModelReleaseId(sessionId);
        if (modelReleaseId == null) {
            modelReleaseId = parseDefaultModelReleaseId(revision.getModelConfig());
        }
        if (modelReleaseId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "会话未选模型且 Revision 未配置默认模型,无法运行");
        }
        AgentModelRelease release = modelControlService.requireUsableRelease(modelReleaseId, session.getOwnerUserId());
        String provider = release.getProvider() == null ? "" : release.getProvider();
        if (!"openai-compatible".equals(provider)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "当前仅支持 openai-compatible 模型,实际 provider=" + provider);
        }
        String plainSecret = secretCipher.decrypt(release.getEncryptedSecret());
        if (plainSecret == null || plainSecret.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "模型 Release 未配置密钥,无法运行");
        }
        Long owner = session.getOwnerUserId() == null ? 0L : session.getOwnerUserId();
        // Revision ∪ Session 绑定合并（Skill/MCP 运行面装配）
        var binding = bindingLoader.load(revisionId, sessionId);
        return new AgentRunPlan(
                sessionId,
                owner,
                boundView.agentDefinitionId() == null ? "" : "agent-" + boundView.agentDefinitionId(),
                revisionId,
                nz(revision.getSystemPrompt()),
                modelReleaseId,
                release.getModelName(),
                release.getBaseUrl(),
                provider,
                DEFAULT_ENDPOINT,
                plainSecret,
                parseAllowedTools(revision.getPermissionPolicy()),
                binding.skills(),
                binding.mcps());
    }

    private AgentSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "sessionId 不能为空");
        }
        AgentSession row = sessionRepository.findById(sessionId);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent session " + sessionId + " not found");
        }
        return row;
    }

    private void checkOwner(AgentSession session, Long requestUserId) {
        if (requestUserId == null) {
            return;
        }
        Long owner = session.getOwnerUserId();
        if (owner != null && owner > 0 && !owner.equals(requestUserId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "无权运行该会话");
        }
    }

    /** 读取会话记住的模型选择（agent_session_model_binding，每会话至多一条）。 */
    private Long resolveSessionModelReleaseId(Long sessionId) {
        AgentSessionModelBinding binding = sessionModelBindingRepository.findBySessionId(sessionId);
        return binding == null ? null : binding.getModelReleaseId();
    }

    /** 读取 Revision model_config JSON 的 default_model_release_id（兼容空/缺省/坏 JSON）。 */
    private Long parseDefaultModelReleaseId(String modelConfig) {
        if (modelConfig == null || modelConfig.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(modelConfig);
            JsonNode v = root == null ? null : root.get(MODEL_CONFIG_DEFAULT_MODEL_KEY);
            if (v == null || v.isNull() || v.isMissingNode()) {
                return null;
            }
            return v.isNumber() ? v.longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 permission_policy JSON 的 allowedTools 数组（缺省为空 = 不放行任何平台工具）。 */
    private List<String> parseAllowedTools(String permissionPolicy) {
        if (permissionPolicy == null || permissionPolicy.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(permissionPolicy);
            JsonNode tools = root == null ? null : root.get(PERMISSION_ALLOWED_TOOLS_KEY);
            if (tools == null || !tools.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            tools.forEach(n -> {
                if (n != null && n.isTextual()) {
                    result.add(n.asText());
                }
            });
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void rejectIfNonBlank(String v, String message) {
        if (v != null && !v.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, message);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
