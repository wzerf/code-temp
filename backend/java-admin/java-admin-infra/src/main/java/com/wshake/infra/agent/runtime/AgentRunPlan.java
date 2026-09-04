package com.wshake.infra.agent.runtime;

import com.wshake.infra.agent.runtime.AgentBindingSnapshot.McpEntry;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.SkillEntry;
import java.util.List;

/**
 * 一次 Agent 运行的计划快照（运行时只读不可变；全部指向已冻结版本）。
 *
 * <p>由 {@link AgentRunPlanner} 在运行前装配：固定 Revision 指针、最终模型 Release 指针、
 * 系统提示词与权限策略，以及 Revision ∪ Session 合并后的 Skill/MCP 绑定装配集。
 *
 * @param sessionId      平台会话 id（= agentscope RuntimeContext.sessionId）
 * @param ownerUserId    会话所有者（= agentscope RuntimeContext.userId）
 * @param agentName      Agent 名（展示）
 * @param revisionId     固定 Revision id（首启后非空）
 * @param systemPrompt   固定 system_prompt
 * @param modelReleaseId 最终模型 Release id（Session 选择优先，回落到 Revision 默认）
 * @param modelName      远端模型标识（快照）
 * @param baseUrl        模型连接根（快照，通常含版本路径）
 * @param provider       openai-compatible / anthropic
 * @param endpointPath   OpenAI 兼容端点路径（默认 /v1/chat/completions，按需覆盖）
 * @param plainSecret    API Key 明文（仅内存，不落日志/DB/审计）
 * @param allowedTools   运行时放行工具白名单（permission_policy.allowedTools）
 * @param skills         合并后的 Skill 装配（skillName → releaseId；可为空列表）
 * @param mcps           合并后的 MCP 装配（mcpName → releaseId + 密钥密文；可为空列表）
 * @author wshake
 */
public record AgentRunPlan(
        Long sessionId,
        Long ownerUserId,
        String agentName,
        Long revisionId,
        String systemPrompt,
        Long modelReleaseId,
        String modelName,
        String baseUrl,
        String provider,
        String endpointPath,
        String plainSecret,
        java.util.List<String> allowedTools,
        List<SkillEntry> skills,
        List<McpEntry> mcps) {}
