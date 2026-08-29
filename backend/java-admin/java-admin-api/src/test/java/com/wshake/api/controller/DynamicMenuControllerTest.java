package com.wshake.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wshake.api.vo.RuntimeMenuRouteVO;
import com.wshake.common.request.RequestContext;
import com.wshake.common.result.Result;
import com.wshake.service.menu.MenuManageModels.RuntimeMenuRoute;
import com.wshake.service.menu.SysMenuService;
import io.github.linpeilie.Converter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link DynamicMenuController} 动态路由契约测试。
 *
 * <p>登录校验由 WebConfig SaInterceptor 负责；本测写入请求上下文。
 */
class DynamicMenuControllerTest {

    private final SysMenuService sysMenuService = mock(SysMenuService.class);
    private final Converter converter = new Converter();
    private final DynamicMenuController controller = new DynamicMenuController(sysMenuService, converter);

    @Test
    void all_returnsRoutesForUser() {
        when(sysMenuService.listRuntimeMenusForUser(7L))
                .thenReturn(List.of(new RuntimeMenuRoute(
                        "System", "/system", null, "/system/user", Map.of("title", "系统"), List.of())));

        RequestContext.open();
        RequestContext.setUserId(7L);
        try {
            Result<List<RuntimeMenuRouteVO>> result = controller.all();
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getPath()).isEqualTo("/system");
            assertThat(result.getData().get(0).getMeta()).containsEntry("title", "系统");
        } finally {
            RequestContext.close();
        }
    }
}
