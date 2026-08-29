package com.wshake.service.agent;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.common.time.TimeZones;
import com.wshake.service.agent.AgentControlModels.AgentDefinitionView;
import com.wshake.service.agent.AgentControlModels.AgentRevisionView;
import com.wshake.service.agent.AgentControlModels.AgentRunPlan;
import com.wshake.service.agent.AgentControlModels.AgentSessionView;
import com.wshake.service.agent.AgentControlModels.CreateAgentCommand;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentSession;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agent Definition、Revision 与固定 Revision 会话的控制面编排。 */
@Service
@RequiredArgsConstructor
public class AgentControlService {

    private final AgentDefinitionRepository definitionRepository;
    private final AgentRevisionRepository revisionRepository;
    private final AgentSessionRepository sessionRepository;

    @Transactional
    public AgentRevisionView createAgent(CreateAgentCommand command) {
        Long ownerUserId = requireOwnerUserId(command.ownerUserId());
        String name = required(command.name(), "name", 128);
        if (definitionRepository.existsByName(name)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent name already exists");
        }
        AgentDefinition definition = new AgentDefinition();
        definition.setName(name);
        definition.setDescription(nullable(command.description()).trim());
        definition.setOwnerUserId(ownerUserId);
        definition.setRemark(nullable(command.remark()).trim());
        definition.setIsEnabled(1);
        definitionRepository.insert(definition);
        return createDraft(
                new CreateRevisionCommand(
                        definition.getId(),
                        command.systemPrompt(),
                        command.modelConfig(),
                        command.permissionPolicy(),
                        command.memoryPolicy(),
                        command.compressionPolicy(),
                        command.remark()),
                ownerUserId);
    }

    public AgentDefinitionView getDefinition(Long id, Long ownerUserId) {
        return toDefinitionView(requireDefinitionOwned(id, ownerUserId));
    }

    @Transactional
    public AgentRevisionView createDraft(CreateRevisionCommand command, Long ownerUserId) {
        AgentDefinition definition = requireDefinitionOwned(command.agentDefinitionId(), ownerUserId);
        requireEnabled(definition);
        AgentRevision draft = newDraft(definition.getId(), command);
        revisionRepository.insert(draft);
        return toRevisionView(draft);
    }

    public AgentRevisionView getRevision(Long id, Long ownerUserId) {
        return toRevisionView(requireRevisionOwned(id, ownerUserId));
    }

    @Transactional
    public AgentRevisionView updateDraft(UpdateRevisionCommand command, Long ownerUserId) {
        AgentRevision draft = requireRevisionOwned(command.id(), ownerUserId);
        requireDraft(draft);
        if (command.systemPromptPresent()) {
            draft.setSystemPrompt(required(command.systemPrompt(), "systemPrompt", 65535));
        }
        if (command.modelConfigPresent()) {
            draft.setModelConfig(AgentJsonSupport.toJson(command.modelConfig(), "modelConfig"));
        }
        if (command.permissionPolicyPresent()) {
            draft.setPermissionPolicy(AgentJsonSupport.toJson(command.permissionPolicy(), "permissionPolicy"));
        }
        if (command.memoryPolicyPresent()) {
            draft.setMemoryPolicy(AgentJsonSupport.toJson(command.memoryPolicy(), "memoryPolicy"));
        }
        if (command.compressionPolicyPresent()) {
            draft.setCompressionPolicy(AgentJsonSupport.toJson(command.compressionPolicy(), "compressionPolicy"));
        }
        if (command.remarkPresent()) {
            draft.setRemark(nullable(command.remark()));
        }
        revisionRepository.update(draft);
        return toRevisionView(draft);
    }

    @Transactional
    public AgentRevisionView publish(Long draftRevisionId, Long ownerUserId) {
        AgentRevision draft = requireRevisionOwned(draftRevisionId, ownerUserId);
        requireDraft(draft);
        AgentDefinition definition = requireDefinitionOwned(draft.getAgentDefinitionId(), ownerUserId);
        requireEnabled(definition);

        AgentRevision published = copyAsPublished(draft);
        revisionRepository.insert(published);
        definition.setCurrentPublishedRevisionId(published.getId());
        definitionRepository.update(definition);
        return toRevisionView(published);
    }

    @Transactional
    public AgentDefinitionView rollback(Long definitionId, Long publishedRevisionId, Long ownerUserId) {
        AgentDefinition definition = requireDefinitionOwned(definitionId, ownerUserId);
        requireEnabled(definition);
        AgentRevision target = requireRevision(publishedRevisionId);
        if (!definition.getId().equals(target.getAgentDefinitionId())
                || !AgentControlModels.REVISION_PUBLISHED.equals(target.getStatus())
                || target.getIsEnabled() == null
                || target.getIsEnabled() == 0) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "revision is not an enabled published revision of this agent");
        }
        definition.setCurrentPublishedRevisionId(target.getId());
        definitionRepository.update(definition);
        return toDefinitionView(definition);
    }

    @Transactional
    public AgentSessionView createSession(Long definitionId, Long ownerUserId) {
        AgentDefinition definition = requireDefinitionOwned(definitionId, ownerUserId);
        requireEnabled(definition);
        AgentSession session = new AgentSession();
        session.setAgentDefinitionId(definition.getId());
        session.setOwnerUserId(ownerUserId);
        session.setStatus(AgentControlModels.SESSION_ACTIVE);
        session.setCreatedAt(TimeZones.now());
        sessionRepository.insert(session);
        return toSessionView(session);
    }

    /** 首次运行入口固定当前发布 Revision；已固定会话绝不切换。 */
    @Transactional
    public AgentSessionView resolveSessionRevision(Long sessionId, Long ownerUserId) {
        AgentSession session = requireSession(sessionId, ownerUserId);
        bindSessionRevision(session, ownerUserId);
        return toSessionView(session);
    }

    /**
     * 解析一次会话运行所需的固定 Revision 输入。
     *
     * <p>首次运行在同一事务中绑定当前发布 Revision；后续运行仅使用该绑定，不重新读取最新发布指针。
     */
    @Transactional
    public AgentRunPlan prepareRun(Long sessionId, Long ownerUserId) {
        AgentSession session = requireSession(sessionId, ownerUserId);
        bindSessionRevision(session, ownerUserId);
        AgentRevision revision = requirePublished(session.getAgentRevisionId(), session.getAgentDefinitionId());
        return new AgentRunPlan(
                session.getId(),
                session.getAgentDefinitionId(),
                revision.getId(),
                session.getOwnerUserId(),
                revision.getSystemPrompt(),
                AgentJsonSupport.parse(revision.getModelConfig(), "modelConfig"),
                AgentJsonSupport.parse(revision.getPermissionPolicy(), "permissionPolicy"),
                AgentJsonSupport.parse(revision.getMemoryPolicy(), "memoryPolicy"),
                AgentJsonSupport.parse(revision.getCompressionPolicy(), "compressionPolicy"));
    }

    public AgentSessionView getSession(Long sessionId, Long ownerUserId) {
        AgentSession session = sessionRepository.findByIdAndOwnerUserId(sessionId, requireOwnerUserId(ownerUserId));
        if (session == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent session not found");
        }
        return toSessionView(session);
    }

    /** 紧急禁用只阻止新会话/首次运行；已固定会话继续由后续运行策略决定。 */
    @Transactional
    public AgentDefinitionView emergencyDisable(Long definitionId, Long ownerUserId) {
        AgentDefinition definition = requireDefinitionOwned(definitionId, ownerUserId);
        definition.setIsEnabled(0);
        definitionRepository.update(definition);
        return toDefinitionView(definition);
    }

    private AgentRevision newDraft(Long definitionId, CreateRevisionCommand command) {
        AgentRevision draft = new AgentRevision();
        draft.setAgentDefinitionId(definitionId);
        draft.setStatus(AgentControlModels.REVISION_DRAFT);
        draft.setSystemPrompt(required(command.systemPrompt(), "systemPrompt", 65535));
        draft.setModelConfig(AgentJsonSupport.toJson(command.modelConfig(), "modelConfig"));
        draft.setPermissionPolicy(AgentJsonSupport.toJson(command.permissionPolicy(), "permissionPolicy"));
        draft.setMemoryPolicy(AgentJsonSupport.toJson(command.memoryPolicy(), "memoryPolicy"));
        draft.setCompressionPolicy(AgentJsonSupport.toJson(command.compressionPolicy(), "compressionPolicy"));
        draft.setRemark(nullable(command.remark()).trim());
        draft.setIsEnabled(1);
        return draft;
    }

    private static AgentRevision copyAsPublished(AgentRevision draft) {
        AgentRevision published = new AgentRevision();
        published.setAgentDefinitionId(draft.getAgentDefinitionId());
        published.setStatus(AgentControlModels.REVISION_PUBLISHED);
        published.setSourceDraftRevisionId(draft.getId());
        published.setSystemPrompt(draft.getSystemPrompt());
        published.setModelConfig(draft.getModelConfig());
        published.setPermissionPolicy(draft.getPermissionPolicy());
        published.setMemoryPolicy(draft.getMemoryPolicy());
        published.setCompressionPolicy(draft.getCompressionPolicy());
        published.setRemark(draft.getRemark());
        published.setIsEnabled(1);
        return published;
    }

    private AgentDefinition requireDefinition(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agentDefinitionId is required");
        }
        AgentDefinition definition = definitionRepository.findById(id);
        if (definition == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent definition not found");
        }
        return definition;
    }

    private AgentDefinition requireDefinitionOwned(Long id, Long ownerUserId) {
        AgentDefinition definition = requireDefinition(id);
        if (!definition.getOwnerUserId().equals(requireOwnerUserId(ownerUserId))) {
            throw BizException.of(ResultCode.AUTH_FORBIDDEN, "agent definition is not owned by current user");
        }
        return definition;
    }

    private AgentRevision requireRevision(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "revisionId is required");
        }
        AgentRevision revision = revisionRepository.findById(id);
        if (revision == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent revision not found");
        }
        return revision;
    }

    private AgentRevision requireRevisionOwned(Long id, Long ownerUserId) {
        AgentRevision revision = requireRevision(id);
        requireDefinitionOwned(revision.getAgentDefinitionId(), ownerUserId);
        return revision;
    }

    private AgentRevision requirePublished(Long revisionId, Long definitionId) {
        AgentRevision revision = requireRevision(revisionId);
        if (!definitionId.equals(revision.getAgentDefinitionId())
                || !AgentControlModels.REVISION_PUBLISHED.equals(revision.getStatus())
                || revision.getIsEnabled() == null
                || revision.getIsEnabled() == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent has no enabled published revision");
        }
        return revision;
    }

    private AgentSession requireSession(Long id, Long ownerUserId) {
        AgentSession session = sessionRepository.findByIdAndOwnerUserId(id, requireOwnerUserId(ownerUserId));
        if (session == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent session not found");
        }
        return session;
    }

    private void bindSessionRevision(AgentSession session, Long ownerUserId) {
        if (session.getAgentRevisionId() != null) {
            return;
        }
        AgentDefinition definition = requireDefinitionOwned(session.getAgentDefinitionId(), ownerUserId);
        requireEnabled(definition);
        AgentRevision published = requirePublished(definition.getCurrentPublishedRevisionId(), definition.getId());
        if (sessionRepository.bindRevisionIfUnbound(session.getId(), ownerUserId, published.getId()) == 0) {
            AgentSession bound = requireSession(session.getId(), ownerUserId);
            session.setAgentRevisionId(bound.getAgentRevisionId());
            if (session.getAgentRevisionId() == null) {
                throw BizException.of(ResultCode.INTERNAL_ERROR, "agent session revision binding failed");
            }
            return;
        }
        session.setAgentRevisionId(published.getId());
    }

    private static void requireDraft(AgentRevision revision) {
        if (!AgentControlModels.REVISION_DRAFT.equals(revision.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only draft revisions can be updated or published");
        }
    }

    private static void requireEnabled(AgentDefinition definition) {
        if (definition.getIsEnabled() == null || definition.getIsEnabled() == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent definition is disabled");
        }
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = nullable(value).trim();
        if (normalized.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is too long");
        }
        return normalized;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private static Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw BizException.of(ResultCode.AUTH_NOT_LOGIN, "owner user is required");
        }
        return ownerUserId;
    }

    private static AgentDefinitionView toDefinitionView(AgentDefinition definition) {
        return new AgentDefinitionView(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getOwnerUserId(),
                definition.getCurrentPublishedRevisionId(),
                definition.getRemark(),
                definition.getIsEnabled(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    private static AgentRevisionView toRevisionView(AgentRevision revision) {
        return new AgentRevisionView(
                revision.getId(),
                revision.getAgentDefinitionId(),
                revision.getStatus(),
                revision.getSourceDraftRevisionId(),
                revision.getSystemPrompt(),
                AgentJsonSupport.parse(revision.getModelConfig(), "modelConfig"),
                AgentJsonSupport.parse(revision.getPermissionPolicy(), "permissionPolicy"),
                AgentJsonSupport.parse(revision.getMemoryPolicy(), "memoryPolicy"),
                AgentJsonSupport.parse(revision.getCompressionPolicy(), "compressionPolicy"),
                revision.getRemark(),
                revision.getCreatedAt(),
                revision.getUpdatedAt());
    }

    private static AgentSessionView toSessionView(AgentSession session) {
        return new AgentSessionView(
                session.getId(),
                session.getAgentDefinitionId(),
                session.getAgentRevisionId(),
                session.getOwnerUserId(),
                session.getStatus(),
                session.getCreatedAt());
    }
}
