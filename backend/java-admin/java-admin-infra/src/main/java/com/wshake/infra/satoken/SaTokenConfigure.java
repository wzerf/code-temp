package com.wshake.infra.satoken;

import cn.dev33.satoken.stp.StpUtil;
import com.wshake.common.request.RequestContext;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 工具。
 *
 * <p>当前 Sa-Token 最新版（1.45.0）的 {@code SaServletFilter} 仍基于
 * {@code javax.servlet.Filter}，与 Spring Boot 4 (Jakarta EE 11) 不兼容。
 * <p>本框架<strong>不</strong>用 {@code SaServletFilter}，仅用 {@code SaInterceptor}
 * （在 {@code WebConfig} 注册）；仅供安全链路在填充 {@link RequestContext} 前读取 Sa-Token。
 *
 * @author wshake
 */
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Spring 需要可实例化的 lite @Configuration bean
public class SaTokenConfigure {

    /**
     * 当前登录用户 id；仅供 RequestContext 填充前的安全链路读取，未登录返回 {@code null}。
     */
    public static Long currentUserIdOrNull() {
        Long fromCtx = RequestContext.userIdOrNull();
        if (fromCtx != null) {
            return fromCtx;
        }
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
