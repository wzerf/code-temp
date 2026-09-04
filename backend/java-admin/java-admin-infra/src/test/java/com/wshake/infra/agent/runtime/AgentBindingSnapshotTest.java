package com.wshake.infra.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.wshake.infra.agent.runtime.AgentBindingSnapshot.McpEntry;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.SkillEntry;
import com.wshake.infra.agent.runtime.AgentBindingSnapshot.Snapshot;
import com.wshake.service.entity.AgentRevisionMcpBinding;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.entity.AgentSessionMcpBinding;
import com.wshake.service.entity.AgentSessionSkillBinding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link AgentBindingSnapshot} Revision∪Session 绑定合并（同名 Session 覆盖）。 */
class AgentBindingSnapshotTest {

    private static AgentRevisionSkillBinding revSkill(String name, Long releaseId) {
        AgentRevisionSkillBinding b = new AgentRevisionSkillBinding();
        b.setSkillName(name);
        b.setSkillReleaseId(releaseId);
        return b;
    }

    private static AgentSessionSkillBinding sessSkill(String name, Long releaseId) {
        AgentSessionSkillBinding b = new AgentSessionSkillBinding();
        b.setSkillName(name);
        b.setSkillReleaseId(releaseId);
        return b;
    }

    private static AgentRevisionMcpBinding revMcp(String name, Long releaseId, String secret) {
        AgentRevisionMcpBinding b = new AgentRevisionMcpBinding();
        b.setMcpName(name);
        b.setMcpReleaseId(releaseId);
        b.setEncryptedSecret(secret);
        return b;
    }

    private static AgentSessionMcpBinding sessMcp(String name, Long releaseId, String secret) {
        AgentSessionMcpBinding b = new AgentSessionMcpBinding();
        b.setMcpName(name);
        b.setMcpReleaseId(releaseId);
        b.setEncryptedSecret(secret);
        return b;
    }

    @Test
    void mergesRevisionAndSessionSkills() {
        Snapshot snap = AgentBindingSnapshot.merge(
                List.of(revSkill("code-reviewer", 11L), revSkill("doc-writer", 12L)),
                List.of(sessSkill("code-reviewer", 99L)),
                List.of(),
                List.of());
        List<SkillEntry> skills = snap.skills();
        assertThat(skills).hasSize(2);
        // Session 同名覆盖 Revision（code-reviewer → 99）
        assertThat(skills).contains(new SkillEntry("code-reviewer", 99L));
        // Revision 独有保留
        assertThat(skills).contains(new SkillEntry("doc-writer", 12L));
    }

    @Test
    void revisionOnlyAndSessionOnly() {
        Snapshot snap = AgentBindingSnapshot.merge(
                List.of(revSkill("a", 1L)), List.of(sessSkill("b", 2L)), List.of(), List.of());
        assertThat(snap.skills()).hasSize(2);
    }

    @Test
    void sessionMcpSecretOverridesRevision() {
        Snapshot snap = AgentBindingSnapshot.merge(
                List.of(),
                List.of(),
                List.of(revMcp("files", 21L, "rev-secret")),
                List.of(sessMcp("files", 22L, "sess-secret")));
        List<McpEntry> mcps = snap.mcps();
        assertThat(mcps).hasSize(1);
        McpEntry entry = mcps.get(0);
        assertThat(entry.mcpName()).isEqualTo("files");
        assertThat(entry.mcpReleaseId()).isEqualTo(22L);
        assertThat(entry.encryptedSecret()).isEqualTo("sess-secret");
    }

    @Test
    void nullBindingsProduceEmptySnapshot() {
        Snapshot snap = AgentBindingSnapshot.merge(null, null, null, null);
        assertThat(snap.skills()).isEmpty();
        assertThat(snap.mcps()).isEmpty();
    }

    @Test
    void mcpRevisionOnlyKeepsSecret() {
        Snapshot snap =
                AgentBindingSnapshot.merge(List.of(), List.of(), List.of(revMcp("db", 31L, "secret")), List.of());
        assertThat(snap.mcps()).containsExactly(new McpEntry("db", 31L, "secret"));
    }
}
