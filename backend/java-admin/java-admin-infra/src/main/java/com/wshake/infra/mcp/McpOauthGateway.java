package com.wshake.infra.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.port.McpOauthPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * MCP OAuth 出站实现（{@link McpOauthPort} 的 OkHttp 适配）。
 *
 * <p>token/client_secret 明文只在本类内存短暂存在，不落库/日志。
 *
 * @author wshake
 */
@Component
@RequiredArgsConstructor
public class McpOauthGateway implements McpOauthPort {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> fetchAuthorizationServerMetadata(String issuerOrigin) {
        if (issuerOrigin == null || issuerOrigin.isBlank()) {
            return null;
        }
        String url = issuerOrigin.endsWith("/")
                ? issuerOrigin + ".well-known/oauth-authorization-server"
                : issuerOrigin + "/.well-known/oauth-authorization-server";
        JsonNode node = getJson(url);
        if (node == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token_endpoint", text(node, "token_endpoint"));
        result.put("registration_endpoint", text(node, "registration_endpoint"));
        result.put("authorization_endpoint", text(node, "authorization_endpoint"));
        return result;
    }

    @Override
    public String dynamicRegister(String registrationEndpoint, String redirectUri) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("redirect_uris", new String[] {redirectUri});
        payload.put("grant_types", new String[] {"authorization_code", "refresh_token"});
        payload.put("response_types", new String[] {"code"});
        payload.put("token_endpoint_auth_method", "none");
        payload.put("client_name", "java-admin-mcp");
        try {
            String json = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(registrationEndpoint)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8")))
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                String text = body == null ? "" : body.string();
                if (!response.isSuccessful()) {
                    throw BizException.of(
                            ResultCode.PARAM_INVALID, "动态注册失败(" + response.code() + ")：" + clip(text, 200));
                }
                String clientId = text(objectMapper.readTree(text), "client_id");
                if (clientId.isBlank()) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "动态注册未返回 client_id");
                }
                return clientId;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "动态注册失败：" + e.getMessage());
        }
    }

    @Override
    public TokenResponse exchangeToken(String tokenEndpoint, Map<String, String> form) {
        FormBody.Builder builder = new FormBody.Builder();
        if (form != null) {
            form.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.add(k, v);
                }
            });
        }
        Request request =
                new Request.Builder().url(tokenEndpoint).post(builder.build()).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "换票失败(" + response.code() + ")：" + clip(text, 200));
            }
            JsonNode node = objectMapper.readTree(text);
            return new TokenResponse(
                    text(node, "access_token"),
                    text(node, "refresh_token"),
                    text(node, "token_type"),
                    node.has("expires_in") ? node.get("expires_in").asLong(0) : 0,
                    text(node, "scope"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_INVALID, "换票请求失败：" + e.getMessage());
        }
    }

    private JsonNode getJson(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return objectMapper.readTree(response.body().string());
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
