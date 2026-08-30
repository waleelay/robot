package com.robot.bigscreen.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import org.springframework.web.server.ResponseStatusException;
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

    @Test
    void readsSmallBodiesAndRejectsOversizedBodies() throws Exception {
        assertThat(CenterProxyClient.readBounded(
                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                3,
                "too large"))
                .containsExactly(1, 2, 3);

        assertThatThrownBy(() -> CenterProxyClient.readBounded(
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}),
                3,
                "too large"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("413");
    }

    @Test
    void keepsMultipartFileAsStreamResource() throws Exception {
        Part part = mock(Part.class);
        when(part.getSubmittedFileName()).thenReturn("snapshot.jpg");
        when(part.getSize()).thenReturn(3L);
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        var resource = CenterProxyClient.filePartResource(part);

        assertThat(resource.getFilename()).isEqualTo("snapshot.jpg");
        assertThat(resource.contentLength()).isEqualTo(3);
        assertThat(resource.getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }
}
