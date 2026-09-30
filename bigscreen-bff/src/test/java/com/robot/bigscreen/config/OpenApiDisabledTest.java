package com.robot.bigscreen.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.robot.bigscreen.client.CenterProxyClient;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import com.robot.bigscreen.statistics.DeviceStatusSampler;
import com.robot.bigscreen.statistics.StatisticsReportStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 文档默认关闭，新增契约能力不能意外开放生产文档端点。 */
@SpringBootTest(classes = OpenApiContractTest.Application.class)
@AutoConfigureMockMvc
class OpenApiDisabledTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PanoramaCenterClient center;
    @MockitoBean private CenterProxyClient proxy;
    @MockitoBean private DeviceStatusSampler sampler;
    @MockitoBean private StatisticsReportStore reports;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void keepsDocumentationDisabledByDefault() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
