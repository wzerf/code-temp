package com.wshake.common.constant;

/**
 * 数据库整数二元标志的值及归一化规则。
 *
 * @author wshake
 */
public final class StatusFlags {

    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    private StatusFlags() {}

    /** {@code null} 使用默认值；其它非零值兼容性归一为 {@link #ENABLED}。 */
    public static int normalize(Integer value, int defaultValue) {
        return value == null ? defaultValue : value == DISABLED ? DISABLED : ENABLED;
    }

    /** {@code null} 使用默认值；布尔值映射为整数标志。 */
    public static int fromBoolean(Boolean value, int defaultValue) {
        return value == null ? defaultValue : value ? ENABLED : DISABLED;
    }

    /** 仅接受 {@link #DISABLED} 与 {@link #ENABLED}。 */
    public static boolean isBinary(int value) {
        return value == DISABLED || value == ENABLED;
    }
}
