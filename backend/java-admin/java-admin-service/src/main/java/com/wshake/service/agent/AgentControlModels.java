package com.wshake.service.agent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Agent 控制面命令与视图；不包含运行面状态。 */
public final class AgentControlModels {

    private AgentControlModels() {}

    public static final String REVISION_DRAFT = "DRAFT";
    public static final String REVISION_PUBLISHED = "PUBLISHED";
    public static final String SESSION_ACTIVE = "ACTIVE";

    public record CreateAgentCommand(
            Long ownerUserId,
            String name,
            String description,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            String remark) {}

    public record SkillBindingCommand(Long skillReleaseId, boolean overrideWinner) {}

    public record SkillBindingView(Long skillReleaseId, String skillName, String contentHash, boolean overrideWinner) {}

    public record SkillSnapshot(
            String name,
            String description,
            String skillContent,
            String source,
            String contentHash,
            Map<String, String> resources) {}

    public record CreateRevisionCommand(
            Long agentDefinitionId,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            String remark,
            List<SkillBindingCommand> skillBindings) {}

    public record UpdateRevisionCommand(
            Long id,
            String systemPrompt,
            boolean systemPromptPresent,
            Map<String, Object> modelConfig,
            boolean modelConfigPresent,
            Map<String, Object> permissionPolicy,
            boolean permissionPolicyPresent,
            Map<String, Object> memoryPolicy,
            boolean memoryPolicyPresent,
            Map<String, Object> compressionPolicy,
            boolean compressionPolicyPresent,
            String remark,
            boolean remarkPresent,
            List<SkillBindingCommand> skillBindings,
            boolean skillBindingsPresent) {}

    public record AgentDefinitionView(
            Long id,
            String name,
            String description,
            Long ownerUserId,
            Long currentPublishedRevisionId,
            String remark,
            Integer isEnabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record AgentRevisionView(
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
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<SkillBindingView> skillBindings) {}

    public record AgentSessionView(
            Long id,
            Long agentDefinitionId,
            Long agentRevisionId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt) {}

    public record AgentSessionMessageView(String id, String role, String content, String thinking, String createdAt) {}

    public record AgentSessionHistoryView(AgentSessionView session, java.util.List<AgentSessionMessageView> messages) {}

    /** 运行面唯一可消费的不可变 Revision 输入；不包含密钥或 Redis 状态。 */
    public record AgentRunPlan(
            Long sessionId,
            Long agentDefinitionId,
            Long agentRevisionId,
            Long ownerUserId,
            String systemPrompt,
            Map<String, Object> modelConfig,
            Map<String, Object> permissionPolicy,
            Map<String, Object> memoryPolicy,
            Map<String, Object> compressionPolicy,
            List<SkillSnapshot> skills) {}

    /** 运行面向 API 暴露的稳定事件载荷；不泄漏 AgentScope SDK 事件。 */
    public record AgentRunEvent(
            String type,
            String requestId,
            Long sessionId,
            Long agentRevisionId,
            String text,
            String toolName,
            String message) {}
}
