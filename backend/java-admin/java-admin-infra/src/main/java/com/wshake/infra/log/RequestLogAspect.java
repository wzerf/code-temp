package com.wshake.infra.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.wshake.common.constant.MdcKeys;
import com.wshake.common.constant.SecurityHeaders;
import com.wshake.common.request.RequestContext;
import com.wshake.service.log.ApiLogWriter;
import com.wshake.service.log.LogManageModels.ApiLogWriteCommand;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller 请求日志切面：fluent API（{@code logType=HTTP}）+ 异步写入 {@code api_log}。
 *
 * <p>成功 / 失败字段走 {@code addKeyValue}（JSON 可检索），标签放 {@code logType} 不进 message；
 * 失败再 {@code setCause} 附堆栈。
 *
 * <p>args 序列化时会跳过 Servlet/文件/流等不可 JSON 化参数，并对密码类字段脱敏。
 * 请求结束后将关键字段异步落入 {@code api_log}（字段对齐 schema）。
 *
 * @author wshake
 */
@Slf4j
@Aspect
@Component
public class RequestLogAspect {

    private static final Splitter PATH_SEGMENTS = Splitter.on('/');

    /** 匹配 JSON 中常见敏感字段的字符串值，替换为 "***"。 */
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(\"(?:password|passwordHash|oldPassword|newPassword|accessToken|refreshToken|token|secret|authorization)\""
                    + "\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "authorization|cookie|set-cookie|x-request-encrypted-key|x-request-signature|x-sign",
            Pattern.CASE_INSENSITIVE);

    /** 由 {@link com.wshake.infra.jackson.JacksonConfig} 注册的全局 Bean 注入。 */
    private final ObjectMapper objectMapper;

    /** 可空：单测可不注入。 */
    private final ApiLogWriter apiLogWriter;

    @Autowired
    public RequestLogAspect(ObjectMapper objectMapper, ApiLogWriter apiLogWriter) {
        this.objectMapper = objectMapper;
        this.apiLogWriter = apiLogWriter;
    }

    /** 单测：仅校验序列化/HTTP 行时可不传 writer。 */
    public RequestLogAspect(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Pointcut("execution(* com.wshake.api.controller..*(..))")
    public void controllerPointcut() {}

    /** 环绕 Controller 方法，记录 HTTP 路径、参数、耗时、返回值，并异步写 api_log。 */
    @Around("controllerPointcut()")
    // CHECKSTYLE.OFF: IllegalThrows
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // CHECKSTYLE.ON: IllegalThrows
        long start = System.currentTimeMillis();
        String handler = pjp.getSignature().toShortString();
        Object[] args = pjp.getArgs();
        String httpLine = currentHttpLine();
        String argsJson = safeToJson(args);
        // Filter 已写入；切面只读，不再重复解析代理头 / IP 归属地
        String clientIp = nullToEmpty(RequestContext.clientIpOrNull());
        String location = nullToEmpty(RequestContext.locationOrNull());

        Long userId = RequestContext.userIdOrNull();
        if (userId != null) {
            MDC.put(MdcKeys.USER_ID, String.valueOf(userId));
        }

        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            log.atInfo()
                    .addKeyValue("logType", "HTTP")
                    .addKeyValue("http", httpLine)
                    .addKeyValue("clientIp", clientIp)
                    .addKeyValue("location", location)
                    .addKeyValue("handler", handler)
                    .addKeyValue("costMs", cost)
                    .addKeyValue("args", argsJson)
                    .addKeyValue("result", safeToJson(result))
                    .log("");
            return result;
        } catch (Throwable t) {
            error = t;
            long cost = System.currentTimeMillis() - start;
            log.atError()
                    .addKeyValue("logType", "HTTP")
                    .addKeyValue("http", httpLine)
                    .addKeyValue("clientIp", clientIp)
                    .addKeyValue("location", location)
                    .addKeyValue("handler", handler)
                    .addKeyValue("costMs", cost)
                    .addKeyValue("args", argsJson)
                    .setCause(t)
                    .log("");
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            writeApiLogQuietly(userId, clientIp, argsJson, result, error, cost);
            MDC.remove(MdcKeys.USER_ID);
        }
    }

    private void writeApiLogQuietly(
            Long userId, String clientIp, String argsJson, Object result, Throwable error, long costMs) {
        if (apiLogWriter == null) {
            return;
        }
        try {
            HttpServletRequest request = currentRequest();
            String method = request != null ? request.getMethod() : "";
            String path = request != null ? request.getRequestURI() : nullToEmpty(RequestContext.requestUriOrNull());
            String query = request != null && request.getQueryString() != null ? request.getQueryString() : "";
            String requestUri = path;
            if (!query.isEmpty()) {
                requestUri = path + "?" + query;
            }

            int statusCode = error == null ? 200 : resolveErrorStatus(error);
            boolean success = statusCode >= 200 && statusCode < 300;
            String reason = error == null ? "" : nullToEmpty(error.getMessage());

            String requestId = RequestContext.requestIdOrNull();
            if (requestId == null || requestId.isBlank()) {
                requestId = request != null ? request.getHeader(SecurityHeaders.REQUEST_ID) : null;
            }
            String userAgent = request != null ? nullToEmpty(request.getHeader(SecurityHeaders.USER_AGENT)) : "";
            String referer = request != null ? nullToEmpty(request.getHeader(SecurityHeaders.REFERER)) : "";
            String headersJson = request != null ? serializeHeaders(request) : "";
            String responseJson = error == null ? safeToJson(result) : "";

            apiLogWriter.record(new ApiLogWriteCommand(
                    method,
                    path,
                    resolveModule(path),
                    statusCode,
                    success,
                    reason,
                    costMs,
                    nullToEmpty(requestId),
                    userId,
                    "",
                    requestUri,
                    query,
                    argsJson,
                    headersJson,
                    referer,
                    responseJson,
                    ApiLogWriter.DEFAULT_CLIENT_ID,
                    "",
                    clientIp,
                    userAgent));
        } catch (Exception e) {
            log.atDebug()
                    .addKeyValue("logType", "API_LOG")
                    .addKeyValue("reason", e.toString())
                    .log("skip record");
        }
    }

    /**
     * 从路径推导 module：{@code /api/system/user/list} → {@code user}；
     * {@code /api/auth/login} → {@code auth}；{@code /api/menu/all} → {@code menu}。
     */
    static String resolveModule(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        List<String> parts = PATH_SEGMENTS.splitToList(p);
        // api / system / <module> / ...
        if (parts.size() >= 3 && "api".equals(parts.get(0)) && "system".equals(parts.get(1))) {
            return parts.get(2);
        }
        // api / <module> / ...
        if (parts.size() >= 2 && "api".equals(parts.get(0))) {
            return parts.get(1);
        }
        return parts.isEmpty() ? "" : parts.get(0);
    }

    private static int resolveErrorStatus(Throwable error) {
        String name = error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String msg = nullToEmpty(error.getMessage()).toLowerCase(Locale.ROOT);
        if (name.contains("auth") || msg.contains("not login") || msg.contains("未登录")) {
            return 401;
        }
        if (name.contains("forbid") || name.contains("permission") || msg.contains("denied")) {
            return 403;
        }
        if (name.contains("notfound") || name.contains("noresource")) {
            return 404;
        }
        if (name.contains("illegal") || name.contains("valid") || name.contains("badrequest")) {
            return 400;
        }
        return 500;
    }

    private String serializeHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return "{}";
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null) {
                continue;
            }
            String value = request.getHeader(name);
            if (SENSITIVE_HEADER.matcher(name).find()) {
                headers.put(name, "***");
            } else {
                headers.put(name, value == null ? "" : value);
            }
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 从当前请求线程组装 {@code METHOD uri?query}；无 Web 请求时返回 {@code -}。
     *
     * <p>包内可见，便于单测。
     */
    static String formatHttpLine(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return method + " " + uri;
        }
        return method + " " + uri + "?" + query;
    }

    private static String currentHttpLine() {
        return formatHttpLine(currentRequest());
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return servletAttrs.getRequest();
    }

    /**
     * 将对象安全格式化为日志用 JSON（包内可见，便于单测）。
     *
     * @param obj 可为 null、POJO 或 Controller 方法参数数组
     * @return 脱敏后的字符串
     */
    String safeToJson(Object obj) {
        return maskSensitive(rawJson(obj));
    }

    private String rawJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof Object[] arr) {
            return formatArgs(arr);
        }
        // ResponseEntity / HttpEntity 序列化会带 headers、status，日志只保留 body
        return writeOrFallback(unwrapResponseBody(obj));
    }

    /** 剥掉 {@link HttpEntity} 包装，只留下响应体（递归处理嵌套包装）。 */
    private static Object unwrapResponseBody(Object obj) {
        if (obj instanceof HttpEntity<?> entity) {
            return unwrapResponseBody(entity.getBody());
        }
        return obj;
    }

    private String formatArgs(Object[] args) {
        List<Object> loggable = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                loggable.add(null);
                continue;
            }
            if (isSkippableArg(arg)) {
                continue;
            }
            loggable.add(arg);
        }

        try {
            return maskSensitive(objectMapper.writeValueAsString(loggable));
        } catch (JsonProcessingException ignored) {
            StringBuilder sb = new StringBuilder(64).append('[');
            for (int i = 0; i < loggable.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(writeOrFallback(loggable.get(i)));
            }
            sb.append(']');
            return maskSensitive(sb.toString());
        }
    }

    /**
     * Web/IO 等无法（或不该）完整 JSON 序列化的参数，跳过以免拖垮整段 args 日志。
     */
    private static boolean isSkippableArg(Object arg) {
        return arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile
                || arg instanceof BindingResult
                || arg instanceof Errors
                || arg instanceof InputStream
                || arg instanceof OutputStream
                || arg instanceof Reader
                || arg instanceof Writer
                || arg instanceof byte[];
    }

    private String writeOrFallback(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // 禁止对数组用 String.valueOf（会得到 [L...;@hash）
            return "\"<" + obj.getClass().getSimpleName() + ">\"";
        }
    }

    private static String maskSensitive(String json) {
        return SENSITIVE_JSON_FIELD.matcher(json).replaceAll("$1\"***\"");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
