package com.wshake.service.port;

import java.util.List;

/**
 * Git Skill 源端口：把 Git 仓库解析为 Skill 包（由 infra 用 JGit 实现）。
 *
 * <p>业务层只依赖本接口，不直接耦合 JGit，便于单测 mock 与安全边界（HTTPS + 拒绝私网）。
 *
 * @author wshake
 */
public interface GitSkillSourcePort {

    /**
     * 解析 ref 为精确 commit SHA 并扫描包（不写草稿）。
     *
     * @param url          HTTPS 地址
     * @param ref          分支/标签/commit
     * @param subdirectory 仓库子目录（空串=根）
     * @return 解析结果
     */
    GitPreview preview(String url, String ref, String subdirectory);

    /**
     * 解析 ref 为服务器 HEAD 的精确 commit SHA。
     *
     * @param url HTTPS 地址
     * @param ref 分支/标签/commit
     * @return 精确 commit SHA
     */
    String resolveHead(String url, String ref);

    /**
     * 读取指定 commit 下某包路径的 SKILL.md 与资源文件。
     *
     * @param url          HTTPS 地址
     * @param ref          分支/标签/commit
     * @param subdirectory 仓库子目录
     * @param skillPath    包相对路径
     * @return 包内容（name/description/skillContent/resources/contentHash）
     */
    GitPackage readPackage(String url, String ref, String subdirectory, String skillPath);

    record GitPackage(
            String skillPath,
            String name,
            String description,
            String skillContent,
            List<GitResource> resources,
            String contentHash) {}

    record GitResource(String resourcePath, String content) {}

    record GitPreview(String commitSha, List<GitPackage> packages) {}
}
