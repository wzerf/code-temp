package com.wshake.common.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link StatusFlags}：整数标志归一化与校验。 */
class StatusFlagsTest {

    @Test
    void normalize_preservesDisabledAndNormalizesNonZero() {
        assertThat(StatusFlags.normalize(null, StatusFlags.ENABLED)).isEqualTo(StatusFlags.ENABLED);
        assertThat(StatusFlags.normalize(StatusFlags.DISABLED, StatusFlags.ENABLED))
                .isEqualTo(StatusFlags.DISABLED);
        assertThat(StatusFlags.normalize(2, StatusFlags.DISABLED)).isEqualTo(StatusFlags.ENABLED);
    }

    @Test
    void fromBooleanAndIsBinary_followIntegerContract() {
        assertThat(StatusFlags.fromBoolean(null, StatusFlags.DISABLED)).isEqualTo(StatusFlags.DISABLED);
        assertThat(StatusFlags.fromBoolean(true, StatusFlags.DISABLED)).isEqualTo(StatusFlags.ENABLED);
        assertThat(StatusFlags.fromBoolean(false, StatusFlags.ENABLED)).isEqualTo(StatusFlags.DISABLED);
        assertThat(StatusFlags.isBinary(StatusFlags.DISABLED)).isTrue();
        assertThat(StatusFlags.isBinary(StatusFlags.ENABLED)).isTrue();
        assertThat(StatusFlags.isBinary(2)).isFalse();
    }
}
