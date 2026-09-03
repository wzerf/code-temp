package com.wshake.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wshake.service.agent.AgentSecretCipher;
import com.wshake.service.agent.AgentSecretProperties;
import com.wshake.service.entity.AgentSkillGitSource;
import com.wshake.service.git.GitSkillSourceService.GitPreviewResult;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillDraftResourceRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link GitSkillSourceService#preview} 真实 JGit 集成:本地 file:// 仓库扫描 SKILL 包。
 */
class GitSkillSourceIntegrationTest {

    private final AgentSkillGitSourceRepository sourceRepo = mock(AgentSkillGitSourceRepository.class);
    private final AgentSkillGitSyncRepository syncRepo = mock(AgentSkillGitSyncRepository.class);
    private final AgentSkillDraftRepository draftRepo = mock(AgentSkillDraftRepository.class);
    private final AgentSkillDraftResourceRepository draftResourceRepo = mock(AgentSkillDraftResourceRepository.class);

    private GitSkillSourceService service;
    private Git git;
    private Path repoDir;

    @TempDir
    Path tmp;

    @BeforeEach
    void init() throws Exception {
        AgentSecretProperties props = new AgentSecretProperties();
        AgentSecretCipher cipher = new AgentSecretCipher(props);
        cipher.init();
        service = new GitSkillSourceService(sourceRepo, syncRepo, draftRepo, draftResourceRepo, cipher);

        repoDir = tmp.resolve("repo");
        Files.createDirectories(repoDir);
        git = Git.init().setDirectory(repoDir.toFile()).call();
        git.commit().setMessage("init").setAllowEmpty(true).call();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (git != null) {
            git.close();
        }
    }

    @Test
    void preview_scansSkillPackagesFromLocalRepo() throws Exception {
        // 包 1:根级 my-skill/SKILL.md + 资源
        Path pkg1 = Files.createDirectories(repoDir.resolve("my-skill"));
        Files.writeString(pkg1.resolve("SKILL.md"), "---\nname: my-skill\ndescription: 测试技能\n---\n# My Skill\n");
        Files.createDirectories(pkg1.resolve("references"));
        Files.writeString(pkg1.resolve("references/usage.md"), "# usage\n");

        // 包 2:sub/other-skill(测试 subdirectory 过滤)
        Path pkg2 = Files.createDirectories(repoDir.resolve("sub").resolve("other-skill"));
        Files.writeString(pkg2.resolve("SKILL.md"), "---\nname: other-skill\ndescription: 另一个\n---\nbody\n");

        addAllAndCommit();

        AgentSkillGitSource source = new AgentSkillGitSource();
        source.setId(1L);
        source.setUrl("file://" + repoDir);
        source.setRef("HEAD");
        source.setSubdirectory("");
        when(sourceRepo.findById(1L)).thenReturn(source);

        GitPreviewResult result = service.preview(1L);

        assertThat(result.commitSha()).hasSize(40);
        assertThat(result.packages()).hasSize(2);
        GitSkillSourceService.SkillPackageScan pkg = result.packages().get(0);
        // 根级 my-skill
        assertThat(pkg.name()).isEqualTo("my-skill");
        assertThat(pkg.description()).isEqualTo("测试技能");
        assertThat(pkg.contentHash()).hasSize(64);
        assertThat(pkg.resources()).isNotEmpty();
    }

    @Test
    void preview_respectsSubdirectory() throws Exception {
        Path pkg2 = Files.createDirectories(repoDir.resolve("sub").resolve("other-skill"));
        Files.writeString(pkg2.resolve("SKILL.md"), "---\nname: other-skill\ndescription: d\n---\nbody\n");
        addAllAndCommit();

        AgentSkillGitSource source = new AgentSkillGitSource();
        source.setId(2L);
        source.setUrl("file://" + repoDir);
        source.setRef("HEAD");
        source.setSubdirectory("sub");
        when(sourceRepo.findById(2L)).thenReturn(source);

        GitPreviewResult result = service.preview(2L);

        assertThat(result.packages()).hasSize(1);
        assertThat(result.packages().get(0).skillPath()).isEqualTo("other-skill");
    }

    private void addAllAndCommit() throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage("add skills").call();
    }
}
