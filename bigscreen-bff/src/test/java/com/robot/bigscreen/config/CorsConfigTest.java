package com.robot.bigscreen.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/** 验证配置来源在 HTTP 和 WebSocket 路径上的跨域行为。 */
class CorsConfigTest {

    @Test
    void appliesConfiguredOriginToWebSocketPath() {
        CorsConfig config = new CorsConfig(List.of(
                "http://localhost:8080",
                "https://175.155.35.79:4443"));
        CorsConfigurationSource source = config.corsConfigurationSource();
        HttpServletRequest request = new MockHttpServletRequest("GET", "/ws/bigscreen");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(
                "https://175.155.35.79:4443",
                configuration.checkOrigin("https://175.155.35.79:4443"));
    }
}
