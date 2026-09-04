package com.wshake.infra.agent.runtime;

import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.entity.AgentSessionMcpBinding;
import com.wshake.service.entity.AgentSessionSkillBinding;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行面绑定合并装配快照（Revision ∪ Session，同名 Session 覆盖 Revision）。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.2：Skill/MCP 最终装配集 = Agent Revision
 * 绑定（发布者预置）∪ Session 绑定（用户侧追加/覆盖），按 name 去重且 Session 优先。
 * 每个条目指向不可变 Release 指针。
 *
 * @author wshake
 */
public final class AgentBindingSnapshot {

    private AgentBindingSnapshot() {}

    /** 合并后的 Skill 装配（skillName → releaseId）。 */
    public record SkillEntry(String skillName, Long skillReleaseId) {}

    /** 合并后的 MCP 装配（mcpName → releaseId + 会话/Agent 级补配的加密密钥）。 */
    public record McpEntry(String mcpName, Long mcpReleaseId, String encryptedSecret) {}

    public record Snapshot(List<SkillEntry> skills, List<McpEntry> mcps) {}

    /**
     * 合并 Revision 与 Session 的 Skill/MCP 绑定。
     *
     * @param revisionSkills revision 绑定（可空）
     * @param sessionSkills  session 绑定（可空）
     * @param revisionMcps   revision MCP 绑定（可空）
     * @param sessionMcps    session MCP 绑定（可空）
     */
    public static Snapshot merge(
            List<AgentRevisionSkillBinding> revisionSkills,
            List<AgentSessionSkillBinding> sessionSkills,
            List<AgentRevisionMcpBinding> revisionMcps,
            List<AgentSessionMcpBinding> sessionMcps) {
        List<SkillEntry> skills = mergeSkills(revisionSkills, sessionSkills);
        List<McpEntry> mcps = mergeMcps(revisionMcps, sessionMcps);
        return new Snapshot(skills, mcps);
    }

    private static List<SkillEntry> mergeSkills(
            List<AgentRevisionSkillBinding> revisionSkills, List<AgentSessionSkillBinding> sessionSkills) {
        Map<String, SkillEntry> byName = new LinkedHashMap<>();
        if (revisionSkills != null) {
            for (AgentRevisionSkillBinding b : revisionSkills) {
                if (b.getSkillName() != null && b.getSkillReleaseId() != null) {
                    byName.putIfAbsent(b.getSkillName(), new SkillEntry(b.getSkillName(), b.getSkillReleaseId()));
                }
            }
        }
        if (sessionSkills != null) {
            for (AgentSessionSkillBinding b : sessionSkills) {
                if (b.getSkillName() != null && b.getSkillReleaseId() != null) {
                    // Session 同名覆盖 Revision
                    byName.put(b.getSkillName(), new SkillEntry(b.getSkillName(), b.getSkillReleaseId()));
                }
            }
        }
        return List.copyOf(byName.values());
    }

    private static List<McpEntry> mergeMcps(
            List<AgentRevisionMcpBinding> revisionMcps, List<AgentSessionMcpBinding> sessionMcps) {
        Map<String, McpEntry> byName = new LinkedHashMap<>();
        if (revisionMcps != null) {
            for (AgentRevisionMcpBinding b : revisionMcps) {
                if (b.getMcpName() != null && b.getMcpReleaseId() != null) {
                    byName.putIfAbsent(
                            b.getMcpName(), new McpEntry(b.getMcpName(), b.getMcpReleaseId(), b.getEncryptedSecret()));
                }
            }
        }
        if (sessionMcps != null) {
            for (AgentSessionMcpBinding b : sessionMcps) {
                if (b.getMcpName() != null && b.getMcpReleaseId() != null) {
                    // Session 同名覆盖 Revision（含密钥：session 补配的密钥优先）
                    byName.put(
                            b.getMcpName(), new McpEntry(b.getMcpName(), b.getMcpReleaseId(), b.getEncryptedSecret()));
                }
            }
        }
        return List.copyOf(byName.values());
    }
}
