package com.robot.bigscreen.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.robot.bigscreen.client.CenterProxyClient;
import com.robot.bigscreen.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessTaskProxyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "bigscreen.auth.client-id=bigscreen-web",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://iam.example/realms/iam-auth",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://iam.example/realms/iam-auth/certs"
})
class BusinessTaskProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CenterProxyClient proxyClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void forwardsSceneResourceGrants() throws Exception {
        when(proxyClient.forwardToManage(any(), eq("/api/v1/management/selection-options/scenes/scene-1/resource-grants")))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        mockMvc.perform(get("/api/bigscreen/business/selection-options/scenes/scene-1/resource-grants").with(jwt()))
                .andExpect(status().isOk());

        verify(proxyClient).forwardToManage(any(), eq("/api/v1/management/selection-options/scenes/scene-1/resource-grants"));
    }

    @Test
    void forwardsSelectionDevices() throws Exception {
        when(proxyClient.forwardToManage(any(), eq("/api/v1/management/selection-options/devices")))
                .thenReturn(ResponseEntity.ok("[]".getBytes()));

        mockMvc.perform(get("/api/bigscreen/business/selection-options/devices").with(jwt()))
                .andExpect(status().isOk());

        verify(proxyClient).forwardToManage(any(), eq("/api/v1/management/selection-options/devices"));
    }

    @Test
    void forwardsWorkflowDefinitionOptions() throws Exception {
        when(proxyClient.forwardToManage(any(), eq("/api/v1/management/selection-options/workflow-definitions/wf-1")))
                .thenReturn(ResponseEntity.ok("{}".getBytes()));

        mockMvc.perform(get("/api/bigscreen/business/selection-options/workflow-definitions/wf-1").with(jwt()))
                .andExpect(status().isOk());

        verify(proxyClient).forwardToManage(any(), eq("/api/v1/management/selection-options/workflow-definitions/wf-1"));
    }

    @Test
    void rejectsUnknownBusinessPath() throws Exception {
        mockMvc.perform(get("/api/bigscreen/business/unknown").with(jwt()))
                .andExpect(status().isNotFound());

        verify(proxyClient, never()).forwardToManage(any(), any());
    }
}
