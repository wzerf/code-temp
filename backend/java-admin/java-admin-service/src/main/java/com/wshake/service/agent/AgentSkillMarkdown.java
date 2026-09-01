package com.wshake.service.agent;

import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import java.util.LinkedHashMap;
import java.util.Map;

/** 解析 SKILL.md YAML frontmatter 的 name / description。 */
final class AgentSkillMarkdown {

    private AgentSkillMarkdown() {}

    record Frontmatter(String name, String description) {}

    static Frontmatter parse(String skillContent) {
        String content = skillContent == null ? "" : skillContent.replace("\r\n", "\n");
        if (!content.startsWith("---\n")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "SKILL.md frontmatter is required");
        }
        int end = content.indexOf("\n---\n", 4);
        if (end < 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "SKILL.md frontmatter is required");
        }
        String block = content.substring(4, end);
        Map<String, String> fields = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (String line : block.split("\n", -1)) {
            int colon = line.indexOf(':');
            boolean newField = colon > 0 && !line.startsWith(" ") && !line.startsWith("\t");
            if (newField) {
                flush(fields, currentKey, currentValue);
                currentKey = line.substring(0, colon).trim();
                currentValue.setLength(0);
                currentValue.append(unquote(line.substring(colon + 1).trim()));
            } else if (currentKey != null) {
                if (!currentValue.isEmpty()) {
                    currentValue.append('\n');
                }
                currentValue.append(line.strip());
            }
        }
        flush(fields, currentKey, currentValue);
        String name = fields.get("name");
        String description = fields.get("description");
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "SKILL.md frontmatter must contain name and description");
        }
        return new Frontmatter(name.trim(), description.trim());
    }

    static void validateResourcePath(String path) {
        if (path == null || path.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "resource path is required");
        }
        if (path.length() > 500) {
            throw BizException.of(ResultCode.PARAM_INVALID, "resource path is too long");
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.contains("..") || path.contains("\\")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "resource path must be a relative printable path");
        }
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch < 32 || ch == 127) {
                throw BizException.of(ResultCode.PARAM_INVALID, "resource path must be a relative printable path");
            }
        }
    }

    static void validateSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is required");
        }
        if (name.length() > 255) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name is too long");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name cannot contain path separators");
        }
    }

    private static void flush(Map<String, String> fields, String key, StringBuilder value) {
        if (key == null || key.isBlank()) {
            return;
        }
        fields.put(key, value.toString().trim());
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        if ("|".equals(value) || ">".equals(value) || "|-".equals(value) || ">".equals(value) || ">-".equals(value)) {
            return "";
        }
        return value;
    }
}
