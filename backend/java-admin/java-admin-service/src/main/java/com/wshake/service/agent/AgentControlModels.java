package com.wshake.service.agent;

import java.time.LocalDateTime;
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
            boolean remarkPresent) {}

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
            LocalDateTime updatedAt) {}

    public record AgentSessionView(
            Long id,
            Long agentDefinitionId,
            Long agentRevisionId,
            Long ownerUserId,
            String status,
            LocalDateTime createdAt) {}
}
