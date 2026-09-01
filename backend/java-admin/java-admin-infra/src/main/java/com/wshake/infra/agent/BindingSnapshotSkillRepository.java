package com.wshake.infra.agent;

import com.wshake.service.agent.AgentControlModels.SkillSnapshot;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 仅暴露本次 Revision Binding 冻结快照的只读 Skill 仓库。 */
public final class BindingSnapshotSkillRepository implements AgentSkillRepository {

    private static final Logger log = LoggerFactory.getLogger(BindingSnapshotSkillRepository.class);

    private final Map<String, AgentSkill> skills = new LinkedHashMap<>();
    private boolean writeable;

    public BindingSnapshotSkillRepository(List<SkillSnapshot> snapshots) {
        if (snapshots != null) {
            for (SkillSnapshot snapshot : snapshots) {
                skills.put(
                        snapshot.name(),
                        new AgentSkill(
                                snapshot.name(),
                                snapshot.description(),
                                snapshot.skillContent(),
                                snapshot.resources(),
                                snapshot.source() == null ? "mysql" : snapshot.source()));
            }
        }
    }

    @Override
    public AgentSkill getSkill(String name) {
        AgentSkill skill = skills.get(name);
        if (skill == null) {
            throw new IllegalArgumentException("Skill not found: " + name);
        }
        return skill;
    }

    @Override
    public List<String> getAllSkillNames() {
        return new ArrayList<>(skills.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    @Override
    public boolean save(List<AgentSkill> skillsToSave, boolean force) {
        if (!writeable) {
            log.warn("Cannot save skills: repository is read-only");
            return false;
        }
        return false;
    }

    @Override
    public boolean delete(String skillName) {
        if (!writeable) {
            log.warn("Cannot delete skill: repository is read-only");
            return false;
        }
        return false;
    }

    @Override
    public boolean skillExists(String skillName) {
        return skills.containsKey(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("binding-snapshot", "revision-binding", writeable);
    }

    @Override
    public String getSource() {
        return "binding_snapshot";
    }

    @Override
    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }
}
