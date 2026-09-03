package com.wshake.service.agent;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.constant.PageLimits;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentMcpReleaseRepository;
import com.wshake.service.repository.AgentRevisionMcpBindingRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import com.wshake.service.repository.AgentRevisionSkillBindingRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 控制面服务：定义 + 草稿 Revision + 发布/回滚/禁用 + Revision/Session 绑定。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class AgentControlService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public static final String VIS_MARKET = "MARKET";
    public static final String VIS_PRIVATE = "PRIVATE";

    private final AgentDefinitionRepository definitionRepository;
    private final AgentRevisionRepository revisionRepository;
    private final AgentRevisionSkillBindingRepository revisionSkillBindingRepository;
    private final AgentRevisionMcpBindingRepository revisionMcpBindingRepository;
    private final AgentSkillReleaseRepository skillReleaseRepository;
    private final AgentMcpReleaseRepository mcpReleaseRepository;
    private final Converter converter;

    // ---------- Agent 定义 ----------

    public PageData<AgentDefinitionView> page(AgentListQuery q) {
        EasyPageResult<AgentDefinition> page =
                definitionRepository.page(q.page(), q.pageSize(), q.nameLike(), q.isEnabled());
        List<AgentDefinition> rows = page.getData();
        if (rows == null) {
            rows = List.of();
        }
        return PageData.of(converter.convert(rows, AgentDefinitionView.class), page.getTotal());
    }

    public List<AgentDefinitionView> listAll(Integer isEnabled) {
        return converter.convert(definitionRepository.listAll(isEnabled), AgentDefinitionView.class);
    }

    public AgentDefinitionView getById(Long id) {
        return converter.convert(requireDefinition(id), AgentDefinitionView.class);
    }

    @Transactional
    public AgentDefinitionView create(CreateAgentCommand cmd) {
        String name = requireUniqueName(cmd.name(), null);
        AgentDefinition row = new AgentDefinition();
        row.setName(name);
        row.setDescription(cmd.description() == null ? "" : cmd.description().trim());
        row.setOwnerUserId(cmd.ownerUserId() == null || cmd.ownerUserId() <= 0 ? 0L : cmd.ownerUserId());
        row.setRemark(cmd.remark() == null ? "" : cmd.remark().trim());
        row.setIsEnabled(cmd.isEnabled() == null ? 1 : normalize01(cmd.isEnabled()));
        definitionRepository.insert(row);
        return converter.convert(requireDefinition(row.getId()), AgentDefinitionView.class);
    }

    @Transactional
    public AgentDefinitionView update(Long id, UpdateAgentCommand cmd) {
        AgentDefinition row = requireDefinition(id);
        if (cmd.name() != null) {
            row.setName(requireUniqueName(cmd.name(), id));
        }
        if (cmd.description() != null) {
            row.setDescription(cmd.description().trim());
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        definitionRepository.update(row);
        return converter.convert(requireDefinition(id), AgentDefinitionView.class);
    }

    @Transactional
    public void softDelete(Long id) {
        requireDefinition(id);
        definitionRepository.softDeleteById(id);
    }

    /** 紧急禁用（只阻止新会话/首次运行）。 */
    @Transactional
    public AgentDefinitionView disable(Long id) {
        requireDefinition(id);
        definitionRepository.updateIsEnabled(id, 0);
        return converter.convert(requireDefinition(id), AgentDefinitionView.class);
    }

    @Transactional
    public AgentDefinitionView enable(Long id) {
        requireDefinition(id);
        definitionRepository.updateIsEnabled(id, 1);
        return converter.convert(requireDefinition(id), AgentDefinitionView.class);
    }

    // ---------- Revision ----------

    /** 获取某 Definition 当前活跃草稿;无则返回 null。 */
    public AgentRevisionView getActiveDraft(Long definitionId) {
        requireDefinition(definitionId);
        AgentRevision draft = revisionRepository.findActiveDraft(definitionId);
        return draft == null ? null : converter.convert(draft, AgentRevisionView.class);
    }

    public List<AgentRevisionView> listRevisions(Long definitionId) {
        requireDefinition(definitionId);
        return converter.convert(revisionRepository.listByDefinitionId(definitionId), AgentRevisionView.class);
    }

    /** 创建草稿 Revision（系统提示词 + 四项 JSON 策略）。 */
    @Transactional
    public AgentRevisionView createDraftRevision(Long definitionId, DraftRevisionCommand cmd) {
        AgentDefinition definition = requireDefinition(definitionId);
        if (revisionRepository.findActiveDraft(definitionId) != null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该 Agent 已有活跃草稿,请直接编辑");
        }
        AgentRevision row = buildDraft(definitionId, cmd);
        revisionRepository.insert(row);
        return converter.convert(requireRevision(row.getId()), AgentRevisionView.class);
    }

    @Transactional
    public AgentRevisionView updateDraftRevision(Long revisionId, DraftRevisionCommand cmd) {
        AgentRevision row = requireRevision(revisionId);
        requireDraftStatus(row);
        if (cmd.systemPrompt() != null) {
            row.setSystemPrompt(cmd.systemPrompt());
        }
        if (cmd.modelConfig() != null) {
            row.setModelConfig(blankToNull(cmd.modelConfig()));
        }
        if (cmd.permissionPolicy() != null) {
            row.setPermissionPolicy(blankToNull(cmd.permissionPolicy()));
        }
        if (cmd.memoryPolicy() != null) {
            row.setMemoryPolicy(blankToNull(cmd.memoryPolicy()));
        }
        if (cmd.compressionPolicy() != null) {
            row.setCompressionPolicy(blankToNull(cmd.compressionPolicy()));
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        revisionRepository.update(row);
        return converter.convert(requireRevision(revisionId), AgentRevisionView.class);
    }

    @Transactional
    public void deleteDraftRevision(Long revisionId) {
        AgentRevision row = requireRevision(revisionId);
        requireDraftStatus(row);
        revisionRepository.softDeleteById(revisionId);
    }

    /**
     * 发布草稿：copyAsPublished 生成新 PUBLISHED 行（不改原草稿）,并复制 Skill/MCP Binding,更新定义指针。
     */
    @Transactional
    public AgentRevisionView publish(Long revisionId) {
        AgentRevision draft = requireRevision(revisionId);
        requireDraftStatus(draft);
        requireBindingsConsistent(draft);

        AgentDefinition definition = requireDefinition(draft.getAgentDefinitionId());
        if (definition.getIsEnabled() == null || definition.getIsEnabled() != 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Agent 已禁用,不可发布");
        }

        AgentRevision published = copyAsPublished(draft);
        revisionRepository.insert(published);
        Long publishedId = published.getId();

        // 复制 Skill binding
        List<AgentRevisionSkillBinding> skillBindings = revisionSkillBindingRepository.listByRevisionId(revisionId);
        if (!skillBindings.isEmpty()) {
            List<AgentRevisionSkillBinding> copies = new ArrayList<>();
            for (AgentRevisionSkillBinding b : skillBindings) {
                AgentRevisionSkillBinding copy = new AgentRevisionSkillBinding();
                copy.setAgentRevisionId(publishedId);
                copy.setSkillReleaseId(b.getSkillReleaseId());
                copy.setSkillName(b.getSkillName());
                copy.setContentHash(b.getContentHash());
                copy.setOverrideWinner(b.getOverrideWinner());
                copies.add(copy);
            }
            revisionSkillBindingRepository.insertAll(copies);
        }
        // 复制 MCP binding
        List<AgentRevisionMcpBinding> mcpBindings = revisionMcpBindingRepository.listByRevisionId(revisionId);
        if (!mcpBindings.isEmpty()) {
            List<AgentRevisionMcpBinding> copies = new ArrayList<>();
            for (AgentRevisionMcpBinding b : mcpBindings) {
                AgentRevisionMcpBinding copy = new AgentRevisionMcpBinding();
                copy.setAgentRevisionId(publishedId);
                copy.setMcpReleaseId(b.getMcpReleaseId());
                copy.setMcpName(b.getMcpName());
                copy.setEncryptedSecret(b.getEncryptedSecret());
                copies.add(copy);
            }
            revisionMcpBindingRepository.insertAll(copies);
        }

        definitionRepository.updateCurrentPublishedRevisionId(definition.getId(), publishedId);
        return converter.convert(requireRevision(publishedId), AgentRevisionView.class);
    }

    /** 回滚：把 current_published_revision_id 指向某个已 PUBLISHED 且未禁用的 Revision。 */
    @Transactional
    public AgentDefinitionView rollback(Long definitionId, Long targetRevisionId) {
        AgentDefinition definition = requireDefinition(definitionId);
        AgentRevision target = requireRevision(targetRevisionId);
        if (!definitionId.equals(target.getAgentDefinitionId())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "目标 Revision 不属于该 Agent");
        }
        if (!STATUS_PUBLISHED.equals(target.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "只能回滚到已发布 Revision");
        }
        definitionRepository.updateCurrentPublishedRevisionId(definitionId, targetRevisionId);
        return converter.convert(requireDefinition(definitionId), AgentDefinitionView.class);
    }

    // ---------- Revision Skill/MCP Binding（发布者预置默认装配） ----------

    public List<SkillBindingView> listRevisionSkillBindings(Long revisionId) {
        requireRevision(revisionId);
        List<AgentRevisionSkillBinding> rows = revisionSkillBindingRepository.listByRevisionId(revisionId);
        List<SkillBindingView> views = new ArrayList<>();
        for (AgentRevisionSkillBinding b : rows) {
            views.add(new SkillBindingView(
                    b.getId(),
                    b.getAgentRevisionId(),
                    b.getSkillReleaseId(),
                    b.getSkillName(),
                    b.getContentHash(),
                    b.getOverrideWinner()));
        }
        return views;
    }

    @Transactional
    public SkillBindingView bindSkillToRevision(Long revisionId, BindSkillCommand cmd) {
        AgentRevision revision = requireRevision(revisionId);
        requireDraftStatus(revision);
        if (revisionSkillBindingRepository.existsName(revisionId, cmd.skillName(), null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该 Revision 已绑定同名 Skill,请先解绑");
        }
        AgentRevisionSkillBinding row = new AgentRevisionSkillBinding();
        row.setAgentRevisionId(revisionId);
        row.setSkillReleaseId(cmd.skillReleaseId());
        row.setSkillName(cmd.skillName());
        row.setContentHash(cmd.contentHash() == null ? "" : cmd.contentHash());
        row.setOverrideWinner(cmd.overrideWinner() == null ? 0 : normalize01(cmd.overrideWinner()));
        revisionSkillBindingRepository.insert(row);
        return listRevisionSkillBindings(revisionId).stream()
                .filter(v -> v.id().equals(row.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void unbindSkillFromRevision(Long bindingId) {
        requireSkillBinding(bindingId);
        revisionSkillBindingRepository.deleteById(bindingId);
    }

    public List<McpBindingView> listRevisionMcpBindings(Long revisionId) {
        requireRevision(revisionId);
        List<AgentRevisionMcpBinding> rows = revisionMcpBindingRepository.listByRevisionId(revisionId);
        List<McpBindingView> views = new ArrayList<>();
        for (AgentRevisionMcpBinding b : rows) {
            views.add(new McpBindingView(
                    b.getId(),
                    b.getAgentRevisionId(),
                    b.getMcpReleaseId(),
                    b.getMcpName(),
                    b.getEncryptedSecret() != null && !b.getEncryptedSecret().isEmpty()));
        }
        return views;
    }

    @Transactional
    public McpBindingView bindMcpToRevision(Long revisionId, BindMcpCommand cmd) {
        AgentRevision revision = requireRevision(revisionId);
        requireDraftStatus(revision);
        if (revisionMcpBindingRepository.existsName(revisionId, cmd.mcpName(), null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "该 Revision 已绑定同名 MCP,请先解绑");
        }
        AgentRevisionMcpBinding row = new AgentRevisionMcpBinding();
        row.setAgentRevisionId(revisionId);
        row.setMcpReleaseId(cmd.mcpReleaseId());
        row.setMcpName(cmd.mcpName());
        row.setEncryptedSecret(
                cmd.encryptedSecret() == null || cmd.encryptedSecret().isEmpty() ? null : cmd.encryptedSecret());
        revisionMcpBindingRepository.insert(row);
        return listRevisionMcpBindings(revisionId).stream()
                .filter(v -> v.id().equals(row.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void unbindMcpFromRevision(Long bindingId) {
        requireMcpBinding(bindingId);
        revisionMcpBindingRepository.deleteById(bindingId);
    }

    /** 校验 Binding 引用存在且（skill 同名）覆盖规则合法。 */
    private void requireBindingsConsistent(AgentRevision draft) {
        List<AgentRevisionSkillBinding> skillBindings = revisionSkillBindingRepository.listByRevisionId(draft.getId());
        for (AgentRevisionSkillBinding b : skillBindings) {
            if (skillReleaseRepository.findById(b.getSkillReleaseId()) == null) {
                throw BizException.of(
                        ResultCode.PARAM_INVALID, "skill binding " + b.getSkillName() + " 引用 Release 不存在,拒绝发布");
            }
        }
        List<AgentRevisionMcpBinding> mcpBindings = revisionMcpBindingRepository.listByRevisionId(draft.getId());
        for (AgentRevisionMcpBinding b : mcpBindings) {
            if (mcpReleaseRepository.findById(b.getMcpReleaseId()) == null) {
                throw BizException.of(
                        ResultCode.PARAM_INVALID, "mcp binding " + b.getMcpName() + " 引用 Release 不存在,拒绝发布");
            }
        }
        // 同名冲突校验:同名 skill 恰好一条 override_winner=1(本期 UI 只允许同名绑定一条,天然满足)
    }

    private AgentRevision buildDraft(Long definitionId, DraftRevisionCommand cmd) {
        AgentRevision row = new AgentRevision();
        row.setAgentDefinitionId(definitionId);
        row.setStatus(STATUS_DRAFT);
        row.setSystemPrompt(cmd.systemPrompt() == null ? "" : cmd.systemPrompt());
        row.setModelConfig(blankToNull(cmd.modelConfig()));
        row.setPermissionPolicy(blankToNull(cmd.permissionPolicy()));
        row.setMemoryPolicy(blankToNull(cmd.memoryPolicy()));
        row.setCompressionPolicy(blankToNull(cmd.compressionPolicy()));
        row.setRemark(cmd.remark() == null ? "" : cmd.remark().trim());
        row.setIsEnabled(1);
        return row;
    }

    private AgentRevision copyAsPublished(AgentRevision draft) {
        AgentRevision row = new AgentRevision();
        row.setAgentDefinitionId(draft.getAgentDefinitionId());
        row.setStatus(STATUS_PUBLISHED);
        row.setSourceDraftRevisionId(draft.getId());
        row.setSystemPrompt(draft.getSystemPrompt());
        row.setModelConfig(draft.getModelConfig());
        row.setPermissionPolicy(draft.getPermissionPolicy());
        row.setMemoryPolicy(draft.getMemoryPolicy());
        row.setCompressionPolicy(draft.getCompressionPolicy());
        row.setRemark(draft.getRemark());
        row.setIsEnabled(1);
        return row;
    }

    private AgentDefinition requireDefinition(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        AgentDefinition row = definitionRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent " + id + " not found");
        }
        return row;
    }

    private AgentRevision requireRevision(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "revisionId 不能为空");
        }
        AgentRevision row = revisionRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent revision " + id + " not found");
        }
        return row;
    }

    private AgentRevisionSkillBinding requireSkillBinding(Long id) {
        AgentRevisionSkillBinding row = revisionSkillBindingRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "skill binding " + id + " not found");
        }
        return row;
    }

    private AgentRevisionMcpBinding requireMcpBinding(Long id) {
        AgentRevisionMcpBinding row = revisionMcpBindingRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "mcp binding " + id + " not found");
        }
        return row;
    }

    private static void requireDraftStatus(AgentRevision row) {
        if (!STATUS_DRAFT.equals(row.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "仅 DRAFT Revision 可编辑/发布");
        }
    }

    private String requireUniqueName(String raw, Long excludeId) {
        String name = raw == null ? null : raw.trim();
        if (name == null || name.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        if (name.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 128 chars");
        }
        AgentDefinition existing = definitionRepository.findByName(name);
        if (existing != null && (excludeId == null || !existing.getId().equals(excludeId))) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent name " + name + " 已存在");
        }
        return name;
    }

    private static int normalize01(Integer value) {
        return value != null && value == 0 ? 0 : 1;
    }

    /** MySQL JSON 列禁止空串:blank → null。 */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    // ---------- 领域模型 ----------

    public record AgentListQuery(int page, int pageSize, String nameLike, Integer isEnabled) {
        public static AgentListQuery of(Integer page, Integer pageSize, String nameLike, Integer isEnabled) {
            return new AgentListQuery(
                    PageLimits.page(page), PageLimits.size(pageSize), trimToNull(nameLike), isEnabled);
        }
    }

    public record CreateAgentCommand(
            String name, String description, String remark, Long ownerUserId, Integer isEnabled) {}

    public record UpdateAgentCommand(String name, String description, String remark) {}

    public record DraftRevisionCommand(
            String systemPrompt,
            String modelConfig,
            String permissionPolicy,
            String memoryPolicy,
            String compressionPolicy,
            String remark) {}

    @io.github.linpeilie.annotations.AutoMapper(target = AgentDefinition.class)
    public record AgentDefinitionView(
            Long id,
            String name,
            String description,
            Long ownerUserId,
            Long currentPublishedRevisionId,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public AgentDefinitionView {
            description = description == null ? "" : description;
            ownerUserId = ownerUserId == null ? 0L : ownerUserId;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    @io.github.linpeilie.annotations.AutoMapper(target = AgentRevision.class)
    public record AgentRevisionView(
            Long id,
            Long agentDefinitionId,
            String status,
            Long sourceDraftRevisionId,
            String systemPrompt,
            String modelConfig,
            String permissionPolicy,
            String memoryPolicy,
            String compressionPolicy,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public AgentRevisionView {
            systemPrompt = systemPrompt == null ? "" : systemPrompt;
            remark = remark == null ? "" : remark;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record SkillBindingView(
            Long id,
            Long agentRevisionId,
            Long skillReleaseId,
            String skillName,
            String contentHash,
            Integer overrideWinner) {}

    public record BindSkillCommand(Long skillReleaseId, String skillName, String contentHash, Integer overrideWinner) {}

    public record McpBindingView(Long id, Long agentRevisionId, Long mcpReleaseId, String mcpName, boolean hasSecret) {}

    public record BindMcpCommand(Long mcpReleaseId, String mcpName, String encryptedSecret) {}

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
