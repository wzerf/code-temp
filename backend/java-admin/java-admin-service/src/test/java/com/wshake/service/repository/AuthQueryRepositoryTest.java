package com.wshake.service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wshake.service.entity.SysMenu;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthQueryRepositoryTest {

    @Test
    void inheritedParentMenuIds_keepsOnlyEnabledMenus() {
        assertThat(AuthQueryRepository.inheritedParentMenuIds(List.of(
                        menu(350L, null, "MENU", null, 1),
                        menu(351L, null, "MENU", null, 0),
                        menu(352L, null, "BUTTON", "agent:conversation:use", 1))))
                .containsExactly(350L);
    }

    @Test
    void inheritedParentMenuIds_returnsEmptyForNoGrantedMenus() {
        assertThat(AuthQueryRepository.inheritedParentMenuIds(List.of())).isEmpty();
    }

    private static SysMenu menu(Long id, Long parentId, String type, String permissionCode, int isEnabled) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setType(type);
        menu.setPermissionCode(permissionCode);
        menu.setIsEnabled(isEnabled);
        return menu;
    }
}
