package com.wshake.infra.web;

import cn.dev33.satoken.stp.StpUtil;
import com.wshake.common.exception.AuthException;
import com.wshake.infra.satoken.SaTokenConfigure;
import com.wshake.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 已登录用户账号状态校验：禁用 / 过期则登出并拒绝请求。
 *
 * <p>须注册在 Sa-Token 之后、LanguageInterceptor 之前（已登录才有 loginId）。
 *
 * @author wshake
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class AccountStatusInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = SaTokenConfigure.currentUserIdOrNull();
        if (userId == null) {
            return true;
        }
        try {
            authService.requireActiveUserById(userId);
            return true;
        } catch (AuthException ex) {
            try {
                if (StpUtil.isLogin()) {
                    StpUtil.logout();
                }
            } catch (Exception logoutEx) {
                log.atDebug()
                        .addKeyValue("logType", "AUTH")
                        .addKeyValue("userId", userId)
                        .setCause(logoutEx)
                        .log("logout after account status reject failed");
            }
            throw ex;
        }
    }
}
