package com.wshake.service.log;

import com.wshake.common.constant.ClientIds;
import com.wshake.common.constant.StatusFlags;
import com.wshake.common.request.RequestContext;
import com.wshake.common.time.TimeZones;
import com.wshake.common.util.UserAgentParser;
import com.wshake.service.entity.ApiLog;
import com.wshake.service.entity.SysUser;
import com.wshake.service.log.LogManageModels.ApiLogWriteCommand;
import com.wshake.service.repository.ApiLogRepository;
import com.wshake.service.repository.SysUserRepository;
import com.wshake.service.support.geo.IpLocationResolver;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * API 调用日志异步写入（对齐 schema {@code api_log} 与 mock {@code appendApiLog}）。
 *
 * <p>IP 归属地 / UA 解析 / insert 不阻塞请求主路径；失败仅打 error 日志。
 *
 * @author wshake
 */
@Slf4j
@Service
public class ApiLogWriter {

    /** 与登录日志一致的客户端 ID 占位。 */
    public static final String DEFAULT_CLIENT_ID = ClientIds.WEB_ADMIN;

    private final ApiLogRepository apiLogRepository;
    private final SysUserRepository sysUserRepository;
    private final IpLocationResolver ipLocationResolver;
    private final Executor apiLogExecutor;

    public ApiLogWriter(
            ApiLogRepository apiLogRepository,
            SysUserRepository sysUserRepository,
            IpLocationResolver ipLocationResolver,
            @Qualifier("apiLogExecutor") Executor apiLogExecutor) {
        this.apiLogRepository = apiLogRepository;
        this.sysUserRepository = sysUserRepository;
        this.ipLocationResolver = ipLocationResolver;
        this.apiLogExecutor = apiLogExecutor;
    }

    /**
     * 异步记录一次 API 调用。
     *
     * @param command 请求线程采集的快照
     */
    public void record(ApiLogWriteCommand command) {
        if (command == null) {
            return;
        }
        LocalDateTime createdAt = TimeZones.now();
        // Filter 已解析则快照带走；异步线程不继承 RequestContext
        String location = RequestContext.locationOrNull();
        apiLogExecutor.execute(() -> insertQuietly(command, createdAt, location));
    }

    private void insertQuietly(ApiLogWriteCommand cmd, LocalDateTime createdAt, String locationHint) {
        try {
            String userAgent = nullToEmpty(cmd.userAgent());
            UserAgentParser.Parsed ua = UserAgentParser.parse(userAgent);
            String location = locationHint != null ? locationHint : ipLocationResolver.resolve(cmd.clientIp());
            String clientName = nullToEmpty(cmd.clientName());
            if (clientName.isBlank()) {
                clientName = ua.clientName();
            }
            if (clientName.isBlank()) {
                clientName = "Web Admin";
            }

            String username = nullToEmpty(cmd.username());
            if (username.isBlank() && cmd.sysUserId() != null) {
                SysUser user = sysUserRepository.findById(cmd.sysUserId());
                if (user != null && user.getUsername() != null) {
                    username = user.getUsername();
                }
            }

            Integer statusCode = cmd.statusCode() == null ? 200 : cmd.statusCode();
            int success = cmd.success() ? StatusFlags.ENABLED : StatusFlags.DISABLED;

            String requestId = nullToEmpty(cmd.requestId());
            if (requestId.isBlank()) {
                requestId = "req-" + UUID.randomUUID().toString().replace("-", "");
            }

            ApiLog row = new ApiLog();
            row.setMethod(
                    nullToEmpty(cmd.method()).isBlank() ? "GET" : cmd.method().toUpperCase(Locale.ROOT));
            row.setModule(nullToEmpty(cmd.module()));
            row.setPath(nullToEmpty(cmd.path()));
            row.setStatusCode(statusCode);
            row.setSuccess(success);
            row.setReason(nullToEmpty(cmd.reason()));
            row.setCostTime(Math.max(0L, cmd.costTimeMs()));
            row.setRequestId(requestId);
            row.setSysUserId(cmd.sysUserId());
            row.setUsername(username);
            row.setRequestUri(nullToEmpty(cmd.requestUri()).isBlank() ? nullToEmpty(cmd.path()) : cmd.requestUri());
            row.setRequestQuery(nullToEmpty(cmd.requestQuery()));
            row.setRequestBody(nullToEmpty(cmd.requestBody()));
            row.setRequestHeader(nullToEmpty(cmd.requestHeader()));
            row.setReferer(nullToEmpty(cmd.referer()));
            row.setResponse(nullToEmpty(cmd.response()));
            row.setBeforeChange("");
            row.setAfterChange("");
            row.setFormatChange("");
            row.setClientId(nullToEmpty(cmd.clientId()).isBlank() ? DEFAULT_CLIENT_ID : cmd.clientId());
            row.setClientName(clientName);
            row.setClientIp(nullToEmpty(cmd.clientIp()));
            row.setUserAgent(userAgent);
            row.setBrowserName(ua.browserName());
            row.setBrowserVersion(ua.browserVersion());
            row.setOsName(ua.osName());
            row.setOsVersion(ua.osVersion());
            row.setLocation(location);
            row.setCreatedAt(createdAt);
            apiLogRepository.insert(row);
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("method", cmd.method())
                    .addKeyValue("path", cmd.path())
                    .addKeyValue("requestId", cmd.requestId())
                    .setCause(e)
                    .addKeyValue("logType", "API_LOG")
                    .log("write failed");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
