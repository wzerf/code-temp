package com.wshake.infra.web;

import cn.dev33.satoken.stp.StpUtil;
import com.wshake.infra.casbin.CasbinInterceptor;
import com.wshake.infra.language.LanguageInterceptor;
import com.wshake.infra.security.SecurityProperties;
import jakarta.servlet.DispatcherType;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 Sa-Token 拦截器 + 账号状态 + Language + jcasbin 鉴权拦截器 + CORS。
 *
 * <p>拦截器顺序：SaInterceptor（认证）→ AccountStatusInterceptor（启用/过期）→
 * LanguageInterceptor（语言上下文/异步偏好）→ CasbinInterceptor（授权）。
 *
 * <p><strong>登录校验的唯一入口</strong>是本类注册的 {@code SaInterceptor}（{@code StpUtil.checkLogin()}）。
 * Controller 不再重复 {@code requireLogin}/{@code isLogin} 门闩；业务侧只在需要时读取 loginId。
 * CasbinInterceptor 仅做授权（deny-by-default），排除登录/登出等公开路径与文档路径。
 *
 * <p>认证/授权拦截器排除路径在 {@code application.yaml} 的 {@code app.security.auth-exclude-paths} /
 * {@code app.security.casbin-exclude-paths} 中配置，未配置时使用 {@link SecurityProperties} 默认值。
 *
 * @author wshake
 */
// final + 无 @Bean 互调：必须用 lite 模式，否则 CGLIB 无法增强 final 类
@Configuration(proxyBeanMethods = false)
public final class WebConfig implements WebMvcConfigurer {

    private final Enforcer casbinEnforcer;
    private final AccountStatusInterceptor accountStatusInterceptor;
    private final LanguageInterceptor languageInterceptor;
    private final SecurityProperties securityProperties;

    public WebConfig(
            Enforcer casbinEnforcer,
            AccountStatusInterceptor accountStatusInterceptor,
            LanguageInterceptor languageInterceptor,
            SecurityProperties securityProperties) {
        this.casbinEnforcer = casbinEnforcer;
        this.accountStatusInterceptor = accountStatusInterceptor;
        this.languageInterceptor = languageInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Sa-Token 认证拦截器：非排除路径强制登录（全站 /api 认证单一真相源）
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response,
                            Object handler) {
                        if (request.getDispatcherType() != DispatcherType.ASYNC) {
                            StpUtil.checkLogin();
                        }
                        return true;
                    }
                })
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getAuthExcludePaths());

        // 2. 账号状态：须在 Sa 之后；过期/禁用则 logout + 403
        registry.addInterceptor(accountStatusInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getAuthExcludePaths());

        // 3. Language：须在 Sa 之后，才能对已登录用户异步收敛 languageCode
        registry.addInterceptor(languageInterceptor).addPathPatterns("/api/**");

        // 4. jcasbin 授权拦截器（deny-by-default；需先加 policy 才能访问）
        registry.addInterceptor(new CasbinInterceptor(casbinEnforcer))
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getCasbinExcludePaths());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // dev 期 CORS 全放开；prod 应收紧
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
