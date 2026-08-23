package com.robot.bigscreen.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.robot.bigscreen.api.BigscreenProxyController;
import com.robot.bigscreen.client.CenterProxyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(BigscreenProxyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "bigscreen.auth.client-id=bigscreen-web",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://iam.example/realms/iam-auth",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://iam.example/realms/iam-auth/certs"
})
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CenterProxyClient proxyClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(proxyClient.forward(any())).thenReturn(ResponseEntity.ok(new byte[0]));
    }

    @Test
    void allowsAnonymousSignedHlsAsset() throws Exception {
        mockMvc.perform(get("/api/control/files/file-001/hls/index.m3u8")
                        .queryParam("token", "signed-play-token"))
                .andExpect(status().isOk());
    }

    @Test
    void protectsRawFileContent() throws Exception {
        mockMvc.perform(get("/api/control/files/file-001/content"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsOtherApiRequests() throws Exception {
        mockMvc.perform(get("/api/bigscreen/panorama/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forwardsCurrentAccessToManagementService() throws Exception {
        when(proxyClient.forwardToManage(any(), eq("/api/v1/management/access-control/me")))
                .thenReturn(ResponseEntity.ok("{}".getBytes()));

        mockMvc.perform(get("/api/bigscreen/access-control/me").with(jwt()))
                .andExpect(status().isOk());

        verify(proxyClient).forwardToManage(any(), eq("/api/v1/management/access-control/me"));
    }

    @Test
    void protectsCurrentAccessRequest() throws Exception {
        mockMvc.perform(get("/api/bigscreen/access-control/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotExposeInternalMediaEndpoints() throws Exception {
        mockMvc.perform(get("/internal/media/video-sessions"))
                .andExpect(status().isNotFound());

        verify(proxyClient, never()).forward(any());
    }

    @Test
    void mapsProxyResourceAccessFailureToBadGateway() throws Exception {
        when(proxyClient.forward(any())).thenThrow(new ResourceAccessException("connect failed"));

        mockMvc.perform(get("/api/control/files/file-001/hls/index.m3u8")
                        .queryParam("token", "signed-play-token"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }
}
