package com.wshake.service.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/**
 * Skill 内容 hash 工具。
 *
 * <p>{@code content_hash} = 对 SKILL.md 与全部资源按 {@code resource_path} 字典序拼接后的
 * 规范化字节做 SHA-256(hex)。拼接规则(可复核):
 * <pre>
 *   for each resource (按 resource_path 字典序):
 *       resourcePath + "\n" + content + "\n"
 * </pre>
 * 主 SKILL.md 以路径 {@code SKILL.md} 参与排序与拼接。
 *
 * @author wshake
 */
public final class SkillContentHasher {

    private SkillContentHasher() {}

    /** 资源路径条目。 */
    public record ResourceEntry(String resourcePath, String content) {}

    /**
     * 计算 content hash;输入为 SKILL.md + 资源列表(路径任意顺序)。
     */
    public static String hash(String skillContent, List<ResourceEntry> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<ResourceEntry> sorted = new java.util.ArrayList<>();
            if (skillContent != null) {
                sorted.add(new ResourceEntry("SKILL.md", skillContent));
            }
            if (resources != null) {
                sorted.addAll(resources);
            }
            sorted.sort(Comparator.comparing(ResourceEntry::resourcePath));
            StringBuilder sb = new StringBuilder();
            for (ResourceEntry e : sorted) {
                sb.append(e.resourcePath()).append('\n').append(e.content()).append('\n');
            }
            byte[] bytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 校验资源路径合法:非空、相对路径、禁止 {@code ..} / 绝对路径 / 反斜杠。
     */
    public static String normalizeResourcePath(String raw) {
        if (raw == null) {
            return null;
        }
        String path = raw.trim().replace('\\', '/');
        if (path.isEmpty()) {
            return null;
        }
        if (path.startsWith("/") || path.contains("..") || path.contains("//")) {
            return null;
        }
        return path;
    }
}
