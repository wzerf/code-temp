package com.wshake.infra.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.exception.SaTokenException;
import com.wshake.common.exception.AuthException;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.Result;
import com.wshake.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 *
 * <p>Q13 决策验证：响应体严格 3 字段，traceId 不在 body。
 *
 * @author wshake
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBiz_returnsCorrectCodeAndStatus() {
        BizException ex = new BizException(4001, "用户已存在");

        ResponseEntity<Result<Object>> resp = handler.handleBiz(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(4001);
        assertThat(resp.getBody().getMsg()).isEqualTo("用户已存在");
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    void handleAuth_returns401_forInvalidCredentials() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.invalidCredentials());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.AUTH_INVALID_CREDENTIALS.getCode());
    }

    @Test
    void handleAuth_returns403_forForbidden() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.forbidden());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.AUTH_FORBIDDEN.getCode());
    }

    @Test
    void handleAuth_returns403_forAccessBlocked_withFixedMsg() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.accessBlocked());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
        assertThat(resp.getBody().getMsg()).isEqualTo("Access Blocked");
    }

    @Test
    void handleAny_returns500_andHidesRealMessage() {
        ResponseEntity<Result<Object>> resp =
                handler.handleAny(new RuntimeException("SQL: SELECT * FROM x WHERE id=1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getMsg()).isEqualTo("内部错误");
        // 真实异常 message 不会泄露
        assertThat(resp.getBody().getMsg()).doesNotContain("SQL");
    }

    @Test
    void handleSaToken_doesNotWriteResultAfterResponseCommitted() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        SaTokenException exception = mock(SaTokenException.class);
        when(exception.getMessage()).thenReturn("上下文尚未初始化");

        assertThat(handler.handleSaToken(exception, response)).isNull();
    }
}
