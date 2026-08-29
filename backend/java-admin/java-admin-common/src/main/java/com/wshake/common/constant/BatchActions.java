package com.wshake.common.constant;

import java.util.Set;

/**
 * 后台批量操作 action 字面量（与前端 batch 接口约定一致）。
 *
 * @author wshake
 */
public final class BatchActions {

    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    public static final String DELETE = "delete";
    public static final String TRIGGER = "trigger";

    /** 通用 CRUD 批量：启用 / 禁用 / 删除。 */
    public static final Set<String> CRUD = Set.of(ENABLE, DISABLE, DELETE);

    /** 任务配置批量：在 CRUD 上增加手动触发。 */
    public static final Set<String> CRUD_WITH_TRIGGER = Set.of(ENABLE, DISABLE, DELETE, TRIGGER);

    public static final String CRUD_HINT = "enable|disable|delete";
    public static final String CRUD_WITH_TRIGGER_HINT = "enable|disable|delete|trigger";

    /** 已通过 CRUD 校验后：{@link #ENABLE} → 启用，其它（通常为 {@link #DISABLE}）→ 禁用。 */
    public static int enabledFlag(String action) {
        return ENABLE.equals(action) ? StatusFlags.ENABLED : StatusFlags.DISABLED;
    }

    private BatchActions() {}
}
