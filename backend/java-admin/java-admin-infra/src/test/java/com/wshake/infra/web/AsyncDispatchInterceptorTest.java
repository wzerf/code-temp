package com.wshake.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wshake.infra.casbin.CasbinInterceptor;
import com.wshake.infra.language.LanguageInterceptor;
import com.wshake.infra.language.UserLanguageSyncService;
import com.wshake.infra.security.SecurityProperties;
import com.wshake.service.auth.AuthService;
import jakarta.servlet.DispatcherType;
import org.casbin.jcasbin.main.Enforcer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AsyncDispatchInterceptorTest {

    @Test
    void asyncDispatch_skipsRequestBoundAuthenticationInterceptors() {
        AuthService authService = mock(AuthService.class);
        UserLanguageSyncService languageSyncService = mock(UserLanguageSyncService.class);
        Enforcer enforcer = mock(Enforcer.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/sessions/3/events");
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(new AccountStatusInterceptor(authService).preHandle(request, response, this))
                .isTrue();
        assertThat(new LanguageInterceptor(new SecurityProperties(), languageSyncService)
                        .preHandle(request, response, this))
                .isTrue();
        assertThat(new CasbinInterceptor(enforcer).preHandle(request, response, this))
                .isTrue();

        verifyNoInteractions(authService, languageSyncService, enforcer);
    }
}
