package com.robot.bigscreen.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CenterProxyClientTest {

    @Test
    void mapsBigscreenControlPathToInternalControlPath() {
        assertThat(CenterProxyClient.targetPath(
                "/api/bigscreen/control/robots/test001/control-profile"))
                .isEqualTo("/api/control/robots/test001/control-profile");
    }

    @Test
    void keepsLegacyBigscreenProxyMapping() {
        assertThat(CenterProxyClient.targetPath("/api/bigscreen/video-sessions/active"))
                .isEqualTo("/api/control/video-sessions/active");
    }
}
