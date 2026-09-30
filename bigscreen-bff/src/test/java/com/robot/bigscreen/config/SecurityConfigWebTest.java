package com.robot.bigscreen.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.robot.bigscreen.api.BigscreenProxyController;
import com.robot.bigscreen.client.CenterProxyClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

/** 验证 BFF 外部入口的认证、调用方限制和代理错误行为。 */
@WebMvcTest(BigscreenProxyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "bigscreen.auth.client-id=bigscreen-web",
        "bigscreen.auth.field-call-client-id=field-app",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://iam.example/realms/iam-auth",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://iam.example/realms/iam-auth/certs"
})
class SecurityConfigWebTest {

    @Test
    void authorizesVideoViewerRoutesForConfiguredClientsOnly() throws Exception {
        for (String path : new String[] {
                "/api/bigscreen/control/robots/robot-1/cameras/camera-1/video/start",
                "/api/bigscreen/control/fixed-cameras/camera-1/video/start",
                "/api/bigscreen/control/video-sessions/session-1/token",
                "/api/bigscreen/control/video-sessions/session-1/heartbeat",
                "/api/bigscreen/control/video-sessions/session-1/stop",
        }) {
            for (String client : new String[] {"field-app", "bigscreen-web"}) {
                mockMvc.perform(post(path)
                                .with(jwt().jwt(token -> token.claim("azp", client))))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(post(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(path)
                            .with(jwt().jwt(token -> token.claim("azp", "other-client"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path)
                            .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void keepsOtherVideoOperationsRestrictedForFieldApp() throws Exception {
        for (String operation : new String[] {
                "restart", "switch-channel", "intercom/start", "recordings/start"
        }) {
            mockMvc.perform(post("/api/bigscreen/control/video-sessions/session-1/" + operation)
                            .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                    .andExpect(status().isForbidden());
        }
    }

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
    void protectsMileageQueryAtBrowserEntry() throws Exception {
        String path = "/api/control/statistics/mileage?startTime=2026-08-14T00:00:00&endTime=2026-08-14T23:59:59";
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk());
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
    void allowsFieldAppTokenToReachStatisticsOverview() throws Exception {
        mockMvc.perform(get("/api/bigscreen/statistics/overview")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isOk());
    }

    @Test
    void allowsFieldAppTokenToReachPanoramaOverview() throws Exception {
        mockMvc.perform(get("/api/bigscreen/panorama/overview")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isOk());
    }

    @Test
    void allowsFieldAppTokenToQueryTaskLists() throws Exception {
        for (String path : new String[] {
                "/api/bigscreen/business/tasks/plans",
                "/api/bigscreen/business/tasks/execution-records",
        }) {
            mockMvc.perform(get(path)
                            .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path)
                            .with(jwt().jwt(token -> token.claim("azp", "other-client"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allowsFieldAppTokenToReachAlarmApiRoute() throws Exception {
        mockMvc.perform(get("/api/bigscreen/panorama/alarms")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isOk());
    }

    @Test
    void allowsBigscreenTokenToReachAlarmApiRoute() throws Exception {
        mockMvc.perform(get("/api/bigscreen/panorama/alarms")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk());
    }

    @Test
    void allowsFieldAppTokenToRequestInlineAlarmSnapshotUrl() throws Exception {
        mockMvc.perform(post("/api/bigscreen/control/files/file-001/download-url")
                        .queryParam("inline", "true")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isOk());
    }

    @Test
    void keepsRawFileContentProtectedForFieldApp() throws Exception {
        mockMvc.perform(get("/api/bigscreen/control/files/file-001/content")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsFieldAppTokenToReachBigscreenWebSocketRoute() throws Exception {
        mockMvc.perform(get("/ws/bigscreen")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowsBigscreenTokenToReachBigscreenWebSocketRoute() throws Exception {
        mockMvc.perform(get("/ws/bigscreen")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBigscreenTokenFromFieldCallWebSocket() throws Exception {
        mockMvc.perform(get("/ws/field-call")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsFieldAppTokenToReachFieldCallWebSocketRoute() throws Exception {
        mockMvc.perform(get("/ws/field-call")
                        .with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void forwardsCurrentAccessToManagementService() throws Exception {
        when(proxyClient.forwardToManage(any(), eq("/api/v1/management/access-control/me")))
                .thenReturn(ResponseEntity.ok("{}".getBytes()));

        mockMvc.perform(get("/api/bigscreen/access-control/me")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
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

    @Test
    void preservesForbiddenStatusFromDownstreamService() throws Exception {
        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"code\":\"403002\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(proxyClient.forward(any())).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "查询 Management 权限失败",
                forbidden));

        mockMvc.perform(get("/api/control/files/file-001/hls/index.m3u8")
                        .queryParam("token", "signed-play-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTHZ_DENIED"))
                .andExpect(jsonPath("$.message").value("当前用户没有访问所需业务资源的权限"));
    }
}
