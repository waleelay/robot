package com.robot.mediaserver.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LiveKitIngressConfigurationValidatorTest {

    @Test
    void acceptsDefaultIngressBudgetWithEightDatabaseConnections() {
        MediaProperties.Livekit livekit = new MediaProperties.Livekit();

        assertThatCode(() -> LiveKitIngressConfigurationValidator.validate(livekit, 8))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDatabasePoolThatCannotReserveSharedCapacity() {
        MediaProperties.Livekit livekit = new MediaProperties.Livekit();

        assertThatThrownBy(() -> LiveKitIngressConfigurationValidator.validate(livekit, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少为 8");
    }

    @Test
    void rejectsLivekitCallTimeoutLongerThanOperationBudget() {
        MediaProperties.Livekit livekit = new MediaProperties.Livekit();
        livekit.setIngressLivekitCallTimeoutMs(6000);

        assertThatThrownBy(() -> LiveKitIngressConfigurationValidator.validate(livekit, 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能超过操作总预算");
    }

    @Test
    void rejectsNonPositiveIngressStatusStaleness() {
        MediaProperties.Livekit livekit = new MediaProperties.Livekit();
        livekit.setIngressStatusStaleSeconds(0);

        assertThatThrownBy(() -> LiveKitIngressConfigurationValidator.validate(livekit, 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须全部大于 0");
    }
}
