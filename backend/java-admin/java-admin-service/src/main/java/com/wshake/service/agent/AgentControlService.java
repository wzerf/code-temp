package com.wshake.service.agent;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.PageData;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentControlModels.AgentListQuery;
import com.wshake.service.agent.AgentControlModels.AgentView;
import com.wshake.service.agent.AgentControlModels.BindingsCommand;
import com.wshake.service.agent.AgentControlModels.BindingsView;
import com.wshake.service.agent.AgentControlModels.CreateAgentCommand;
import com.wshake.service.agent.AgentControlModels.CreateRevisionCommand;
import com.wshake.service.agent.AgentControlModels.McpBindingCommand;
import com.wshake.service.agent.AgentControlModels.McpBindingView;
import com.wshake.service.agent.AgentControlModels.RevisionView;
import com.wshake.service.agent.AgentControlModels.SkillBindingCommand;
import com.wshake.service.agent.AgentControlModels.SkillBindingView;
import com.wshake.service.agent.AgentControlModels.UpdateAgentCommand;
import com.wshake.service.agent.AgentControlModels.UpdateRevisionCommand;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevision;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.repository.AgentDefinitionRepository;
import com.wshake.service.repository.AgentRevisionBindingRepository;
import com.wshake.service.repository.AgentRevisionRepository;
import io.github.linpeilie.Converter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 控制面 Service：定义/草稿/发布/回滚/紧急禁用/绑定。
 *
 * @author wshake
 */
@Service
@RequiredArgsConstructor
public class AgentControlService {

    private final AgentDefinitionRepository definitionRepository;
    private final AgentRevisionRepository revisionRepository;
    private final AgentRevisionBindingRepository bindingRepository;
    private final Converter converter;

    public PageData<AgentView> page(AgentListQuery query) {
        EasyPageResult<AgentDefinition> page =
                definitionRepository.page(query.page(), query.pageSize(), query.name(), query.status());
        List<AgentDefinition> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(converter.convert(rows, AgentView.class), page.getTotal());
    }

    public List<AgentView> listAll() {
        return converter.convert(definitionRepository.listAll(), AgentView.class);
    }

    public AgentView getById(Long id) {
        return converter.convert(requireDefinition(id), AgentView.class);
    }

    @Transactional
    public AgentView create(CreateAgentCommand cmd) {
        String name = requireName(cmd.name());
        if (definitionRepository.existsByName(name, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent name already exists");
        }
        AgentDefinition row = new AgentDefinition();
        row.setName(name);
        row.setDescription(nullToEmpty(cmd.description()));
        row.setOwnerUserId(cmd.ownerUserId() == null ? 0L : cmd.ownerUserId());
        row.setRemark(nullToEmpty(cmd.remark()));
        row.setIsEnabled(normalize01(cmd.isEnabled(), 1));
        definitionRepository.insert(row);
        return converter.convert(requireDefinition(row.getId()), AgentView.class);
    }

    @Transactional
    public AgentView update(UpdateAgentCommand cmd) {
        AgentDefinition row = requireDefinition(cmd.id());
        if (cmd.name() != null) {
            String name = requireName(cmd.name());
            if (definitionRepository.existsByName(name, row.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "agent name already exists");
            }
            row.setName(name);
        }
        if (cmd.description() != null) {
            row.setDescription(cmd.description().trim());
        }
        if (cmd.ownerUserId() != null) {
            row.setOwnerUserId(cmd.ownerUserId());
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        if (cmd.isEnabled() != null) {
            row.setIsEnabled(normalize01(cmd.isEnabled(), 1));
        }
        definitionRepository.update(row);
        return converter.convert(requireDefinition(row.getId()), AgentView.class);
    }

    @Transactional
    public AgentView softDelete(Long id) {
        AgentDefinition row = requireDefinition(id);
        AgentView snapshot = converter.convert(row, AgentView.class);
        long n = definitionRepository.softDeleteById(id);
        if (n == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "agent " + id + " not found");
        }
        return snapshot;
    }

    @Transactional
    public AgentView emergencyDisable(Long id) {
        AgentDefinition row = requireDefinition(id);
        definitionRepository.updateIsEnabled(id, 0);
        return converter.convert(requireDefinition(id), AgentView.class);
    }

    // ---------- Revision ----------

    public List<RevisionView> listRevisions(Long definitionId) {
        requireDefinition(definitionId);
        return revisionRepository.listByDefinition(definitionId).stream()
                .map(this::toRevisionView)
                .toList();
    }

    @Transactional
    public RevisionView createDraft(CreateRevisionCommand cmd) {
        requireDefinition(cmd.agentDefinitionId());
        AgentRevision row = new AgentRevision();
        row.setAgentDefinitionId(cmd.agentDefinitionId());
        row.setStatus(AgentControlModels.STATUS_DRAFT);
        row.setSystemPrompt(cmd.systemPrompt());
        row.setModelConfig(AgentJsonSupport.toJson(cmd.modelConfig(), "modelConfig"));
        row.setPermissionPolicy(AgentJsonSupport.toJson(cmd.permissionPolicy(), "permissionPolicy"));
        row.setMemoryPolicy(AgentJsonSupport.toJson(cmd.memoryPolicy(), "memoryPolicy"));
        row.setCompressionPolicy(AgentJsonSupport.toJson(cmd.compressionPolicy(), "compressionPolicy"));
        row.setRemark(nullToEmpty(cmd.remark()));
        row.setIsEnabled(1);
        revisionRepository.insert(row);
        return toRevisionView(requireRevision(row.getId()));
    }

    @Transactional
    public RevisionView updateDraft(UpdateRevisionCommand cmd) {
        AgentRevision row = requireRevision(cmd.id());
        requireDraft(row);
        if (cmd.systemPrompt() != null) {
            row.setSystemPrompt(cmd.systemPrompt());
        }
        if (cmd.modelConfig() != null) {
            row.setModelConfig(AgentJsonSupport.toJson(cmd.modelConfig(), "modelConfig"));
        }
        if (cmd.permissionPolicy() != null) {
            row.setPermissionPolicy(AgentJsonSupport.toJson(cmd.permissionPolicy(), "permissionPolicy"));
        }
        if (cmd.memoryPolicy() != null) {
            row.setMemoryPolicy(AgentJsonSupport.toJson(cmd.memoryPolicy(), "memoryPolicy"));
        }
        if (cmd.compressionPolicy() != null) {
            row.setCompressionPolicy(AgentJsonSupport.toJson(cmd.compressionPolicy(), "compressionPolicy"));
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark().trim());
        }
        revisionRepository.update(row);
        return toRevisionView(requireRevision(row.getId()));
    }

    /**
     * 发布：复制草稿为新 PUBLISHED 快照（不改草稿），复制绑定，更新 Definition 当前指针。
     */
    @Transactional
    public RevisionView publish(Long draftRevisionId) {
        AgentRevision draft = requireRevision(draftRevisionId);
        requireDraft(draft);

        AgentRevision published = new AgentRevision();
        published.setAgentDefinitionId(draft.getAgentDefinitionId());
        published.setStatus(AgentControlModels.STATUS_PUBLISHED);
        published.setSourceDraftRevisionId(draft.getId());
        published.setSystemPrompt(draft.getSystemPrompt());
        published.setModelConfig(draft.getModelConfig());
        published.setPermissionPolicy(draft.getPermissionPolicy());
        published.setMemoryPolicy(draft.getMemoryPolicy());
        published.setCompressionPolicy(draft.getCompressionPolicy());
        published.setRemark(draft.getRemark());
        published.setIsEnabled(1);
        revisionRepository.insert(published);

        copySkillBindings(draft.getId(), published.getId());
        copyMcpBindings(draft.getId(), published.getId());

        definitionRepository.updateCurrentPublishedRevision(draft.getAgentDefinitionId(), published.getId());
        return toRevisionView(requireRevision(published.getId()));
    }

    /**
     * 回滚：把 Definition 的当前发布指针指向某个已启用 PUBLISHED Revision。
     */
    @Transactional
    public RevisionView rollback(Long definitionId, Long targetRevisionId) {
        requireDefinition(definitionId);
        AgentRevision target = requireRevision(targetRevisionId);
        if (!target.getAgentDefinitionId().equals(definitionId)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "revision does not belong to agent");
        }
        if (!AgentControlModels.STATUS_PUBLISHED.equals(target.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "rollback target must be PUBLISHED");
        }
        definitionRepository.updateCurrentPublishedRevision(definitionId, target.getId());
        return toRevisionView(requireRevision(target.getId()));
    }

    // ---------- Bindings ----------

    public BindingsView getBindings(Long revisionId) {
        requireRevision(revisionId);
        List<SkillBindingView> skills = bindingRepository.listSkillBindings(revisionId).stream()
                .map(SkillBindingView::from)
                .toList();
        List<McpBindingView> mcps = bindingRepository.listMcpBindings(revisionId).stream()
                .map(McpBindingView::from)
                .toList();
        return new BindingsView(skills, mcps);
    }

    @Transactional
    public BindingsView setBindings(Long revisionId, BindingsCommand cmd) {
        AgentRevision revision = requireRevision(revisionId);
        requireDraft(revision);

        bindingRepository.deleteSkillBindings(revisionId);
        bindingRepository.deleteMcpBindings(revisionId);

        if (cmd.skills() != null) {
            for (SkillBindingCommand s : cmd.skills()) {
                AgentRevisionSkillBinding row = new AgentRevisionSkillBinding();
                row.setAgentRevisionId(revisionId);
                row.setSkillReleaseId(s.skillReleaseId());
                row.setSkillName(s.skillName());
                row.setContentHash(s.contentHash() == null ? "" : s.contentHash());
                row.setOverrideWinner(s.overrideWinner() == null ? 0 : (s.overrideWinner() == 0 ? 0 : 1));
                bindingRepository.insertSkillBinding(row);
            }
        }
        if (cmd.mcps() != null) {
            for (McpBindingCommand m : cmd.mcps()) {
                AgentRevisionMcpBinding row = new AgentRevisionMcpBinding();
                row.setAgentRevisionId(revisionId);
                row.setMcpReleaseId(m.mcpReleaseId());
                row.setMcpName(m.mcpName());
                row.setEncryptedSecret(m.encryptedSecret());
                bindingRepository.insertMcpBinding(row);
            }
        }
        return getBindings(revisionId);
    }

    // ---------- helpers ----------

    private void copySkillBindings(Long sourceRevisionId, Long targetRevisionId) {
        for (AgentRevisionSkillBinding b : bindingRepository.listSkillBindings(sourceRevisionId)) {
            AgentRevisionSkillBinding row = new AgentRevisionSkillBinding();
            row.setAgentRevisionId(targetRevisionId);
            row.setSkillReleaseId(b.getSkillReleaseId());
            row.setSkillName(b.getSkillName());
            row.setContentHash(b.getContentHash());
            row.setOverrideWinner(b.getOverrideWinner());
            bindingRepository.insertSkillBinding(row);
        }
    }

    private void copyMcpBindings(Long sourceRevisionId, Long targetRevisionId) {
        for (AgentRevisionMcpBinding b : bindingRepository.listMcpBindings(sourceRevisionId)) {
            AgentRevisionMcpBinding row = new AgentRevisionMcpBinding();
            row.setAgentRevisionId(targetRevisionId);
            row.setMcpReleaseId(b.getMcpReleaseId());
            row.setMcpName(b.getMcpName());
            row.setEncryptedSecret(b.getEncryptedSecret());
            bindingRepository.insertMcpBinding(row);
        }
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
            throw BizException.of(ResultCode.PARAM_INVALID, "revision " + id + " not found");
        }
        return row;
    }

    private static void requireDraft(AgentRevision revision) {
        if (!AgentControlModels.STATUS_DRAFT.equals(revision.getStatus())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "only DRAFT revision can be modified/published");
        }
    }

    private RevisionView toRevisionView(AgentRevision r) {
        return new RevisionView(
                r.getId(),
                r.getAgentDefinitionId(),
                r.getStatus(),
                r.getSourceDraftRevisionId(),
                r.getSystemPrompt(),
                AgentJsonSupport.parseObject(r.getModelConfig(), "modelConfig"),
                AgentJsonSupport.parseObject(r.getPermissionPolicy(), "permissionPolicy"),
                AgentJsonSupport.parseObject(r.getMemoryPolicy(), "memoryPolicy"),
                AgentJsonSupport.parseObject(r.getCompressionPolicy(), "compressionPolicy"),
                r.getRemark(),
                r.getIsEnabled(),
                r.getCreatedAt(),
                r.getUpdatedAt());
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int normalize01(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 0 ? 0 : 1;
    }
}
