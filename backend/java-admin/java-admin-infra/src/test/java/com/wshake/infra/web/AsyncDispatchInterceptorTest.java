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
    void skipsSecurityInterceptorsForAsyncDispatch() {
        AuthService authService = mock(AuthService.class);
        UserLanguageSyncService languageSyncService = mock(UserLanguageSyncService.class);
        Enforcer enforcer = mock(Enforcer.class);
        SecurityProperties securityProperties = new SecurityProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(new AccountStatusInterceptor(authService).preHandle(request, response, null))
                .isTrue();
        assertThat(new LanguageInterceptor(securityProperties, languageSyncService).preHandle(request, response, null))
                .isTrue();
        assertThat(new CasbinInterceptor(enforcer).preHandle(request, response, null))
                .isTrue();

        verifyNoInteractions(authService, languageSyncService, enforcer);
    }
}
