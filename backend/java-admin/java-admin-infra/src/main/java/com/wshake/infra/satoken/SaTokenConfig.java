package com.wshake.infra.satoken;

import com.wshake.common.request.RequestContext;
import org.springframework.context.annotation.Configuration;

/** Sa-Token 基础配置占位；业务读取统一经 {@link RequestContext}。 */
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SaTokenConfig {

    public static Long currentUserIdOrNull() {
        return RequestContext.userIdOrNull();
    }
}
