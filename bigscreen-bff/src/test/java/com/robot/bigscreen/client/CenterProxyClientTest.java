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

    @Test
    void stripsAccessTokenFromForwardedQuery() {
        assertThat(CenterProxyClient.stripAccessToken(null)).isNull();
        assertThat(CenterProxyClient.stripAccessToken("inline=true")).isEqualTo("inline=true");
        assertThat(CenterProxyClient.stripAccessToken("access_token=abc&page=1"))
                .isEqualTo("page=1");
        assertThat(CenterProxyClient.stripAccessToken("page=1&access_token=abc"))
                .isEqualTo("page=1");
        assertThat(CenterProxyClient.stripAccessToken("access_token=abc"))
                .isEmpty();
        assertThat(CenterProxyClient.stripAccessToken("a=1&access_token=abc&b=2"))
                .isEqualTo("a=1&b=2");
    }
}
