package com.wshake.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 模块 JSON 字段序列化工具（对齐 TaskJsonSupport）。
 *
 * @author wshake
 */
public final class AgentJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentJsonSupport() {}

    public static String toJson(Map<String, Object> value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is not valid JSON");
        }
    }

    public static Map<String, Object> parseObject(String json, String field) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is not valid JSON object");
        }
    }

    public static Map<String, String> parseStringMap(String json, String field) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is not valid JSON object");
        }
    }

    public static String headersToJson(Map<String, String> headers, String field) {
        if (headers == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(headers);
        } catch (Exception e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is not valid");
        }
    }
}
