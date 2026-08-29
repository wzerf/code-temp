package com.wshake.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private AgentJsonSupport() {}

    static String toJson(Map<String, Object> value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " must be a JSON object");
        }
    }

    static Map<String, Object> parse(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(value, MAP_TYPE);
            return parsed == null ? null : new LinkedHashMap<>(parsed);
        } catch (JsonProcessingException e) {
            throw BizException.of(ResultCode.INTERNAL_ERROR, field + " is invalid JSON");
        }
    }
}
