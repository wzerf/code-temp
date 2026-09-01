package com.wshake.service.agent;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Skill 包内容 hash：SKILL.md + 按 path 字典序的资源，SHA-256 hex。 */
final class AgentSkillContentHash {

    private AgentSkillContentHash() {}

    static String sha256(String skillContent, Map<String, String> resources) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes(skillContent));
            if (resources != null) {
                resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    digest.update(bytes(entry.getKey()));
                    digest.update(bytes(entry.getValue() == null ? "" : entry.getValue()));
                });
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, "skill content hash is unavailable");
        }
    }

    static String sha256(String value) {
        return sha256(value, Map.of());
    }

    private static byte[] bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }
}
