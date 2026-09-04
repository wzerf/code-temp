package com.wshake.infra.agent.runtime;

import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.entity.AgentSkillReleaseResource;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillReleaseResourceRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 平台 Skill 只读仓库：从绑定冻结的 Release 快照构建 {@link AgentSkill}。
 *
 * <p>对齐 docs/agent-module-architecture.md §7.1/§7.4：运行面经自定义加载器读取 Binding
 * 冻结的 Release 快照（{@code agent_skill_release.skill_content} + 冻结资源文件）接入 Skill，
 * 不依赖 agentscope 的 MysqlSkillRepository，也不动态读最新 Release。
 *
 * <p>装配时机：每次运行由 {@link AgentBindingAssembler} 按 plan 的合并 Skill 装配集
 * 构建本仓库并注册到 HarnessAgent（dynamic skill 加载器消费 getAllSkills）。
 *
 * @author wshake
 */
@RequiredArgsConstructor
public class BindingSkillRepository implements AgentSkillRepository {

    private final List<AgentSkill> skills;
    private final String sessionLabel;

    /**
     * 从合并装配集解析 release 快照并构建技能列表。
     *
     * @param entries               合并后的 Skill 装配（skillName → releaseId）
     * @param skillReleaseRepository release 读取
     * @param resourceRepository    冻结资源读取
     * @param sessionLabel          会话标识（仅日志/来源展示）
     */
    public static BindingSkillRepository assemble(
            List<AgentBindingSnapshot.SkillEntry> entries,
            AgentSkillReleaseRepository skillReleaseRepository,
            AgentSkillReleaseResourceRepository resourceRepository,
            String sessionLabel) {
        List<AgentSkill> loaded = new ArrayList<>();
        if (entries != null) {
            for (AgentBindingSnapshot.SkillEntry entry : entries) {
                AgentSkillRelease release = skillReleaseRepository.findById(entry.skillReleaseId());
                if (release == null || release.getIsEnabled() == null || release.getIsEnabled() != 1) {
                    throw new IllegalStateException("skill release " + entry.skillReleaseId() + " 不可用(binding 引用缺失)");
                }
                Map<String, String> resources = new LinkedHashMap<>();
                List<AgentSkillReleaseResource> files = resourceRepository.listByReleaseId(release.getId());
                if (files != null) {
                    for (AgentSkillReleaseResource f : files) {
                        resources.put(f.getResourcePath(), f.getContent());
                    }
                }
                loaded.add(new AgentSkill(
                        release.getName(),
                        nz(release.getDescription()),
                        nz(release.getSkillContent()),
                        resources,
                        "platform:" + sessionLabel));
            }
        }
        return new BindingSkillRepository(loaded, sessionLabel);
    }

    @Override
    public AgentSkill getSkill(String name) {
        return skills.stream().filter(s -> name.equals(s.getName())).findFirst().orElse(null);
    }

    @Override
    public List<String> getAllSkillNames() {
        return skills.stream().map(AgentSkill::getName).toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return skills;
    }

    @Override
    public boolean save(List<AgentSkill> list, boolean force) {
        return false; // 只读仓库：平台 skill 不可由 agent 写入
    }

    @Override
    public boolean delete(String skillName) {
        return false;
    }

    @Override
    public boolean skillExists(String skillName) {
        return skills.stream().anyMatch(s -> skillName.equals(s.getName()));
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("platform-binding", "session " + sessionLabel, false);
    }

    @Override
    public String getSource() {
        return "platform:" + sessionLabel;
    }

    @Override
    public void setWriteable(boolean writeable) {
        // 只读,忽略
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
