package com.wshake.service.agent;

import com.wshake.common.constant.PageLimits;
import com.wshake.service.entity.AgentDefinition;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 管理领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author wshake
 */
public final class AgentControlModels {

    private AgentControlModels() {}

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public record AgentListQuery(int page, int pageSize, String name, Integer status) {

        public static AgentListQuery of(Integer page, Integer pageSize, String name, Integer status) {
            return new AgentListQuery(PageLimits.page(page), PageLimits.size(pageSize), trimToNull(name), status);
        }
    }

    public record CreateAgentCommand(
            String name, String description, Long ownerUserId, String remark, Integer isEnabled) {}

    public record UpdateAgentCommand(
            Long id, String name, String description, Long ownerUserId, String remark, Integer isEnabled) {}

    @AutoMapper(target = AgentDefinition.class)
    public record AgentView(
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
            Long updatedBy) {}

    public record CreateRevisionCommand(
            Long agentDefinitionId,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            String remark) {}

    public record UpdateRevisionCommand(
            Long id,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            String remark) {}

    public record RevisionView(
            Long id,
            Long agentDefinitionId,
            String status,
            Long sourceDraftRevisionId,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            String remark,
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record SkillBindingCommand(
            Long skillReleaseId, String skillName, String contentHash, Integer overrideWinner) {}

    public record McpBindingCommand(Long mcpReleaseId, String mcpName, String encryptedSecret) {}

    public record BindingsCommand(List<SkillBindingCommand> skills, List<McpBindingCommand> mcps) {}

    public record SkillBindingView(Long skillReleaseId, String skillName, String contentHash, Integer overrideWinner) {

        public static SkillBindingView from(AgentRevisionSkillBinding b) {
            return new SkillBindingView(
                    b.getSkillReleaseId(), b.getSkillName(), b.getContentHash(), b.getOverrideWinner());
        }
    }

    public record McpBindingView(Long mcpReleaseId, String mcpName, String encryptedSecret) {

        public static McpBindingView from(AgentRevisionMcpBinding b) {
            return new McpBindingView(b.getMcpReleaseId(), b.getMcpName(), b.getEncryptedSecret());
        }
    }

    public record BindingsView(List<SkillBindingView> skills, List<McpBindingView> mcps) {}

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeEnum(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
