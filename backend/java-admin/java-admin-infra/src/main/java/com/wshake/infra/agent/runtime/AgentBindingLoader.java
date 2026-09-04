package com.wshake.infra.agent.runtime;

import com.wshake.infra.agent.runtime.AgentBindingSnapshot.McpEntry;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.SkillEntry;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.Snapshot;
import com.wshake.service.repository.AgentRevisionMcpBindingRepository;
import com.wshake.service.repository.AgentRevisionSkillBindingRepository;
import com.wshake.service.repository.AgentSessionMcpBindingRepository;
import com.wshake.service.repository.AgentSessionSkillBindingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话运行面绑定装载：Revision 绑定 ∪ Session 绑定（同名 Session 覆盖），产出合并装配集。
 *
 * <p>对齐 docs/agent-module-architecture.md §5.2。读取后由 Skill/MCP 装配器按
 * Release 指针解析冻结快照，运行时绝不动态读最新 Release。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class AgentBindingLoader {

    private final AgentRevisionSkillBindingRepository revisionSkillBindingRepository;
    private final AgentSessionSkillBindingRepository sessionSkillBindingRepository;
    private final AgentRevisionMcpBindingRepository revisionMcpBindingRepository;
    private final AgentSessionMcpBindingRepository sessionMcpBindingRepository;

    /**
     * 装载指定会话（及其固定 Revision）的合并绑定。
     *
     * @param revisionId 会话已固定的 Revision id（必非空）
     * @param sessionId  平台会话 id
     */
    public Snapshot load(Long revisionId, Long sessionId) {
        return AgentBindingSnapshot.merge(
                revisionSkillBindingRepository.listByRevisionId(revisionId),
                sessionSkillBindingRepository.listBySessionId(sessionId),
                revisionMcpBindingRepository.listByRevisionId(revisionId),
                sessionMcpBindingRepository.listBySessionId(sessionId));
    }

    /** 便捷取 Skill 装配（可能为空列表）。 */
    public List<SkillEntry> skills(Snapshot snapshot) {
        return snapshot.skills();
    }

    /** 便捷取 MCP 装配（可能为空列表）。 */
    public List<McpEntry> mcps(Snapshot snapshot) {
        return snapshot.mcps();
    }
}
