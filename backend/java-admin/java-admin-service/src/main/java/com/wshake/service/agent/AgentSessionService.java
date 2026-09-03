package com.wshake.service.agent;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.constant.PageLimits;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentSession;
import com.wshake.service.entity.AgentSessionMcpBinding;
import com.wshake.service.entity.AgentSessionSkillBinding;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentSessionMcpBindingRepository;
import com.wshake.service.repository.AgentSessionRepository;
import com.wshake.service.repository.AgentSessionSkillBindingRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 会话控制面服务：创建/列表/删除会话、Revision 固定、Session 级 Skill/MCP 绑定。
 *
 * <p>运行状态（消息历史/事件/锁）在 Redis,由后续运行面实现；本服务只维护 MySQL 控制面。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository sessionRepository;
    private final AgentDefinitionRepository definitionRepository;
    private final AgentRevisionRepository revisionRepository;
    private final AgentSessionSkillBindingRepository sessionSkillBindingRepository;
    private final AgentSessionMcpBindingRepository sessionMcpBindingRepository;
    private final AgentSkillReleaseRepository skillReleaseRepository;
    private final AgentMcpReleaseRepository mcpReleaseRepository;
    private final Converter converter;

    // ---------- 会话 ----------

    public PageData<AgentSessionView> pageByAgent(Long agentId, SessionListQuery q) {
        definitionRepository.findById(agentId); // 校验存在
        EasyPageResult<AgentSession> page =
                sessionRepository.pageByDefinition(q.page(), q.pageSize(), agentId, q.ownerUserId());
        List<AgentSession> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        return PageData.of(converter.convert(rows, AgentSessionView.class), page.getTotal());
    }

    public AgentSessionView getSession(Long sessionId) {
        return converter.convert(requireSession(sessionId), AgentSessionView.class);
    }

    /** 创建 ACTIVE 会话：agent_revision_id 为空,首启运行时才固定。 */
    @Transactional
    public AgentSessionView createSession(Long agentId, CreateSessionCommand cmd) {
        AgentDefinition definition = requireDefinition(agentId);
        if (definition.getIsEnabled() == null || definition.getIsEnabled() != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Agent 已禁用,不可创建会话");
        }
        AgentSession row = new AgentSession();
        row.setAgentDefinitionId(agentId);
        row.setOwnerUserId(cmd.ownerUserId() == null || cmd.ownerUserId() <= 0 ? 0L : cmd.ownerUserId());
        row.setStatus("ACTIVE");
        row.setRemark(cmd.remark() == null ? "" : cmd.remark().trim());
        row.setIsEnabled(1);
        sessionRepository.insert(row);
        return converter.convert(requireSession(row.getId()), AgentSessionView.class);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        // 会话绑定随会话物理删除（子表无软删）
        sessionSkillBindingRepository
                .listBySessionId(sessionId)
                .forEach(b -> sessionSkillBindingRepository.deleteById(b.getId()));
        sessionMcpBindingRepository
                .listBySessionId(sessionId)
                .forEach(b -> sessionMcpBindingRepository.deleteById(b.getId()));
        sessionRepository.softDeleteById(sessionId);
    }

    /**
     * 首次运行固定 Revision：若会话尚未绑定,读取 Definition 当前发布 Revision 并写入。
     * 并发安全：调用方在运行面配合锁;此处保证「未绑定才绑定」的语义。
     */
    @Transactional
    public AgentSessionView bindSessionRevision(Long sessionId) {
        AgentSession session = requireSession(sessionId);
        if (session.getAgentRevisionId() != null) {
            return converter.convert(session, AgentSessionView.class);
        }
        AgentDefinition definition = requireDefinition(session.getAgentDefinitionId());
        Long publishedId = definition.getCurrentPublishedRevisionId();
        if (publishedId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Agent 尚未发布任何 Revision,无法固定");
        }
        sessionRepository.updateRevisionId(sessionId, publishedId);
        return converter.convert(requireSession(sessionId), AgentSessionView.class);
    }

    // ---------- Session Skill 绑定（用户侧追加/覆盖） ----------

    public List<SessionSkillBindingView> listSessionSkillBindings(Long sessionId) {
        requireSession(sessionId);
        List<AgentSessionSkillBinding> rows = sessionSkillBindingRepository.listBySessionId(sessionId);
        List<SessionSkillBindingView> views = new ArrayList<>();
        for (AgentSessionSkillBinding b : rows) {
            views.add(new SessionSkillBindingView(
                    b.getId(), b.getSessionId(), b.getSkillReleaseId(), b.getSkillName(), b.getContentHash()));
        }
        return views;
    }

    @Transactional
    public SessionSkillBindingView bindSkillToSession(Long sessionId, BindSessionSkillCommand cmd) {
        requireSession(sessionId);
        if (sessionSkillBindingRepository.existsName(sessionId, cmd.skillName(), null)) {
            // 同名覆盖语义：解绑旧的再绑新的
            sessionSkillBindingRepository.listBySessionId(sessionId).stream()
                    .filter(b -> b.getSkillName().equals(cmd.skillName()))
                    .forEach(b -> sessionSkillBindingRepository.deleteById(b.getId()));
        }
        AgentSessionSkillBinding row = new AgentSessionSkillBinding();
        row.setSessionId(sessionId);
        row.setSkillReleaseId(cmd.skillReleaseId());
        row.setSkillName(cmd.skillName());
        row.setContentHash(cmd.contentHash() == null ? "" : cmd.contentHash());
        sessionSkillBindingRepository.insert(row);
        return listSessionSkillBindings(sessionId).stream()
                .filter(v -> v.id().equals(row.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void unbindSkillFromSession(Long bindingId) {
        AgentSessionSkillBinding row = sessionSkillBindingRepository.findById(bindingId);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "session skill binding " + bindingId + " not found");
        }
        sessionSkillBindingRepository.deleteById(bindingId);
    }

    // ---------- Session MCP 绑定 ----------

    public List<SessionMcpBindingView> listSessionMcpBindings(Long sessionId) {
        requireSession(sessionId);
        List<AgentSessionMcpBinding> rows = sessionMcpBindingRepository.listBySessionId(sessionId);
        List<SessionMcpBindingView> views = new ArrayList<>();
        for (AgentSessionMcpBinding b : rows) {
            views.add(new SessionMcpBindingView(
                    b.getId(),
                    b.getSessionId(),
                    b.getMcpReleaseId(),
                    b.getMcpName(),
                    b.getEncryptedSecret() != null && !b.getEncryptedSecret().isEmpty()));
        }
        return views;
    }

    @Transactional
    public SessionMcpBindingView bindMcpToSession(Long sessionId, BindSessionMcpCommand cmd) {
        requireSession(sessionId);
        if (sessionMcpBindingRepository.existsName(sessionId, cmd.mcpName(), null)) {
            sessionMcpBindingRepository.listBySessionId(sessionId).stream()
                    .filter(b -> b.getMcpName().equals(cmd.mcpName()))
                    .forEach(b -> sessionMcpBindingRepository.deleteById(b.getId()));
        }
        AgentSessionMcpBinding row = new AgentSessionMcpBinding();
        row.setSessionId(sessionId);
        row.setMcpReleaseId(cmd.mcpReleaseId());
        row.setMcpName(cmd.mcpName());
        row.setEncryptedSecret(
                cmd.encryptedSecret() == null || cmd.encryptedSecret().isEmpty() ? null : cmd.encryptedSecret());
        sessionMcpBindingRepository.insert(row);
        return listSessionMcpBindings(sessionId).stream()
                .filter(v -> v.id().equals(row.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void unbindMcpFromSession(Long bindingId) {
        AgentSessionMcpBinding row = sessionMcpBindingRepository.findById(bindingId);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "session mcp binding " + bindingId + " not found");
        }
        sessionMcpBindingRepository.deleteById(bindingId);
    }

    // ---------- 内部 ----------

    private AgentSession requireSession(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "sessionId 不能为空");
        }
        AgentSession row = sessionRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent session " + id + " not found");
        }
        return row;
    }

    private AgentDefinition requireDefinition(Long id) {
        AgentDefinition row = definitionRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent " + id + " not found");
        }
        return row;
    }

    // ---------- 领域模型 ----------

    public record SessionListQuery(int page, int pageSize, Long ownerUserId) {
        public static SessionListQuery of(Integer page, Integer pageSize, Long ownerUserId) {
            return new SessionListQuery(PageLimits.page(page), PageLimits.size(pageSize), ownerUserId);
        }
    }

    public record CreateSessionCommand(String remark, Long ownerUserId) {}

    @io.github.linpeilie.annotations.AutoMapper(target = AgentSession.class)
    public record AgentSessionView(
            Long id,
            Long agentDefinitionId,
            Long agentRevisionId,
            Long ownerUserId,
            String status,
            LocalDateTime lastActiveAt,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public AgentSessionView {
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record SessionSkillBindingView(
            Long id, Long sessionId, Long skillReleaseId, String skillName, String contentHash) {}

    public record BindSessionSkillCommand(Long skillReleaseId, String skillName, String contentHash) {}

    public record SessionMcpBindingView(
            Long id, Long sessionId, Long mcpReleaseId, String mcpName, boolean hasSecret) {}

    public record BindSessionMcpCommand(Long mcpReleaseId, String mcpName, String encryptedSecret) {}
}
