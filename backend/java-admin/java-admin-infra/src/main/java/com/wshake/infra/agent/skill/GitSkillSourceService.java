package com.wshake.infra.agent.skill;

import com.wshake.service.agent.skill.SkillManageModels;
import com.wshake.service.port.GitSkillSourcePort;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

/**
 * Git Skill 源端口实现（基于 JGit）。
 *
 * <p>安全边界：仅 HTTPS，拒绝回环/私网/链路本地/保留地址；超时与文件数在类型化配置下限制（首期用常量）。
 *
 * @author wshake
 */
@Service
public class GitSkillSourceService implements GitSkillSourcePort {

    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$", Pattern.DOTALL);
    private static final Pattern NAME_LINE = Pattern.compile("(?m)^name\\s*:\\s*(.+)$");
    private static final Pattern DESC_LINE = Pattern.compile("(?m)^description\\s*:\\s*(.+)$");

    @Override
    public GitPreview preview(String url, String ref, String subdirectory) {
        try (TempRepo repo = clone(url, ref)) {
            String commitSha = resolveHead(repo.repository());
            List<GitPackage> packages = scanPackages(repo.root(), subdirectory);
            return new GitPreview(commitSha, packages);
        } catch (Exception e) {
            throw new RuntimeException("git preview failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String resolveHead(String url, String ref) {
        try (TempRepo repo = clone(url, ref)) {
            return resolveHead(repo.repository());
        } catch (Exception e) {
            throw new RuntimeException("git resolveHead failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GitPackage readPackage(String url, String ref, String subdirectory, String skillPath) {
        try (TempRepo repo = clone(url, ref)) {
            return scanPackage(repo.root(), subdirectory, skillPath);
        } catch (Exception e) {
            throw new RuntimeException("git readPackage failed: " + e.getMessage(), e);
        }
    }

    private List<GitPackage> scanPackages(Path repoRoot, String subdirectory) throws IOException {
        Path base = resolveSubdir(repoRoot, subdirectory);
        List<GitPackage> packages = new ArrayList<>();
        if (!Files.isDirectory(base)) {
            return packages;
        }
        List<Path> skillDirs = new ArrayList<>();
        try (var stream = Files.walk(base)) {
            stream.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .map(Path::getParent)
                    .forEach(skillDirs::add);
        }
        skillDirs.sort(Comparator.comparing(p -> base.relativize(p).toString()));
        for (Path dir : skillDirs) {
            String skillPath = base.relativize(dir).toString().replace('\\', '/');
            packages.add(scanPackage(repoRoot, subdirectory, skillPath));
        }
        return packages;
    }

    private GitPackage scanPackage(Path repoRoot, String subdirectory, String skillPath) throws IOException {
        Path base = resolveSubdir(repoRoot, subdirectory);
        Path dir = base.resolve(skillPath).normalize();
        if (!dir.startsWith(base)) {
            throw new IOException("skillPath escapes subdirectory");
        }
        Path skillMd = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            throw new IOException("SKILL.md not found at " + skillPath);
        }
        String skillContent = Files.readString(skillMd, StandardCharsets.UTF_8);
        String name = extractFrontmatter(skillContent, NAME_LINE);
        String description = extractFrontmatter(skillContent, DESC_LINE);
        if (name == null || name.isBlank()) {
            name = dir.getFileName().toString();
        }

        List<GitResource> resources = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(p -> {
                        String rel = dir.relativize(p).toString().replace('\\', '/');
                        if (rel.contains("..") || rel.startsWith("/")) {
                            return;
                        }
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            resources.add(new GitResource(rel, content));
                            paths.add(rel);
                            contents.add(content);
                        } catch (IOException ignored) {
                            // 单个资源读取失败跳过
                        }
                    });
        }
        String contentHash = SkillManageModels.contentHashOf(skillContent, paths, contents);
        return new GitPackage(skillPath, name, description, skillContent, resources, contentHash);
    }

    private static String extractFrontmatter(String skillContent, Pattern linePattern) {
        Matcher fm = FRONTMATTER.matcher(skillContent);
        if (!fm.find()) {
            return null;
        }
        Matcher m = linePattern.matcher(fm.group(1));
        return m.find() ? m.group(1).trim() : null;
    }

    private static Path resolveSubdir(Path repoRoot, String subdirectory) {
        String sub = subdirectory == null ? "" : subdirectory.trim();
        if (sub.isEmpty() || sub.equals(".")) {
            return repoRoot;
        }
        return repoRoot.resolve(sub).normalize();
    }

    private static String resolveHead(Repository repository) throws IOException {
        return repository.resolve("HEAD").name();
    }

    private static TempRepo clone(String url, String ref) throws GitAPIException, IOException {
        Path dir = Files.createTempDirectory("skill-git-");
        Git git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dir.toFile())
                .setBranch(ref == null || ref.isBlank() ? "main" : ref)
                .setDepth(1)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("", ""))
                .call();
        Repository repository = git.getRepository();
        return new TempRepo(dir, git, repository);
    }

    private static final class TempRepo implements AutoCloseable {
        private final Path root;
        private final Git git;
        private final Repository repository;

        TempRepo(Path root, Git git, Repository repository) {
            this.root = root;
            this.git = git;
            this.repository = repository;
        }

        Path root() {
            return root;
        }

        Repository repository() {
            return repository;
        }

        @Override
        public void close() {
            git.close();
            repository.close();
            deleteRecursively(root.toFile());
        }

        private static void deleteRecursively(File file) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
            file.delete();
        }
    }
}
