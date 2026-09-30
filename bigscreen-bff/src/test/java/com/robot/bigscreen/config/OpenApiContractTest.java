package com.robot.bigscreen.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.robot.bigscreen.api.BigscreenProxyController;
import com.robot.bigscreen.api.BusinessTaskProxyController;
import com.robot.bigscreen.client.CenterProxyClient;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import com.robot.bigscreen.panorama.PanoramaController;
import com.robot.bigscreen.panorama.PanoramaService;
import com.robot.bigscreen.statistics.DeviceStatusSampler;
import com.robot.bigscreen.statistics.StatisticsController;
import com.robot.bigscreen.statistics.StatisticsReportStore;
import com.robot.bigscreen.statistics.StatisticsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 使用真实聚合服务校验 BFF Schema、分页和空值；仅隔离下游、JWT 解码及报告存储。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc
class OpenApiContractTest {
    @Autowired private MockMvc mvc;
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper mapper;
    @Autowired private RequestMappingHandlerMapping mappings;
    @Autowired private StatisticsService statistics;
    @MockitoBean private PanoramaCenterClient center;
    @MockitoBean private CenterProxyClient proxy;
    @MockitoBean private DeviceStatusSampler sampler;
    @MockitoBean private StatisticsReportStore reports;
    @MockitoBean private JwtDecoder jwtDecoder;
    private JsonNode contract;

    @BeforeEach
    void setUp() throws Exception {
        contract = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        when(center.alarmPage(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PanoramaCenterClient.AlarmPage(List.of(), 0, 1, 20));
        when(sampler.countsInRange(any(), any(), any())).thenReturn(new long[]{0, 0, 0, 0});
    }

    @Test
    void coversAllConcreteMappingsAndReferencesEveryProxyFamily() throws Exception {
        Set<String> actual = new HashSet<>();
        Set<String> proxies = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.robot.bigscreen")) {
                mapping.getPatternValues().forEach(path -> {
                    if (path.contains("**")) {
                        proxies.add(path);
                    } else {
                        actual.add(path);
                    }
                });
            }
        });
        Set<String> documented = new HashSet<>();
        contract.path("paths").fieldNames().forEachRemaining(documented::add);
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
        Set<String> referenced = new HashSet<>();
        contract.path("x-proxy-contracts").forEach(item -> referenced.add(item.path("source").asText()));
        assertThat(referenced).containsExactlyInAnyOrderElementsOf(proxies).hasSize(6);
        Set<String> unavailableOperations = new HashSet<>();
        contract.path("paths").forEach(path -> path.forEach(operation -> {
            if (operation.path("responses").has("503")) {
                unavailableOperations.add(operation.path("operationId").asText());
            }
        }));
        assertThat(unavailableOperations).containsExactlyInAnyOrder(
                "statisticsController_overview", "statisticsController_exportReport");
        Path snapshot = Path.of("../quality/openapi/bff.json");
        String generated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(contract)) + "\n";
        Files.createDirectories(Path.of("target/openapi"));
        Files.writeString(Path.of("target/openapi/bff.json"), generated);
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(snapshot, generated);
        }
        assertThat(contract).isEqualTo(mapper.readTree(snapshot.toFile()));
    }

    @TestFactory
    Stream<DynamicTest> validatesRealAggregationResponsesIncludingNoData() {
        return List.of("overview", "devices/{deviceId}/mounted-device-count", "maps/{mapId}/resources",
                "maps/{mapId}/task-routes", "tasks/{taskId}", "tasks/{taskId}/fixed-cameras", "tasks", "alarms",
                "alarms/page", "alarms/actionable-workflow").stream().map(suffix -> DynamicTest.dynamicTest(suffix, () -> {
                    String template = "/api/bigscreen/panorama/" + suffix;
                    String url = template.replace("{deviceId}", "robot-1").replace("{mapId}", "map-1").replace("{taskId}", "task-1");
                    String response = mvc.perform(get(url).with(jwt().jwt(token -> token.subject("user-1").claim("azp", "bigscreen-web"))))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    validate(template, "get", 200, response);
                }));
    }

    @Test
    void validatesRealStatisticsAndPopulatedReportPage() throws Exception {
        String path = "/api/bigscreen/statistics/overview";
        String response = mvc.perform(get(path).with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        validate(path, "get", 200, response);
        when(reports.list(any())).thenReturn(List.of(new StatisticsReportStore.ReportRecord("report-1", "报告", "report.pdf",
                LocalDateTime.of(2026, 9, 29, 12, 0), "PDF", "COMPLETED", "key", "user-1", null)));
        path = "/api/bigscreen/statistics/reports";
        response = mvc.perform(get(path).param("page", "0").param("size", "1000")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        validate(path, "get", 200, response);
        assertThat(mapper.readTree(response).path("size").asInt()).isEqualTo(100);
        String farPage = mvc.perform(get(path).param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100")
                        .with(jwt().jwt(token -> token.subject("user-1").claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(farPage).path("data").size()).isZero();
        mvc.perform(get("/api/bigscreen/statistics/reports/absent/download")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesPopulatedStatisticsAndRejectsBrokenNestedFields() throws Exception {
        when(center.devices()).thenReturn(List.of(Map.of("serialNumber", "robot-1", "deviceType", "ROBOT")));
        when(center.deviceTypeOptions()).thenReturn(List.of(Map.of("value", "ROBOT", "label", "机器人")));
        when(center.taskWorkflowInstancesForStatistics()).thenReturn(List.of(Map.of(
                "status", "COMPLETED", "completedAt", LocalDateTime.now().toString())));
        when(center.alarmsForStatistics(any(), any())).thenReturn(List.of(Map.of(
                "alarmType", "FIRE", "alarmTime", LocalDateTime.now().toString(), "areaName", "测试区域")));
        String path = "/api/bigscreen/statistics/overview";
        String response = mvc.perform(get(path).with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        validate(path, "get", 200, response);
        ObjectNode changed = (ObjectNode) mapper.readTree(response);
        assertThat(changed.path("equipmentRuntime").path("items").size()).isEqualTo(1);
        ((ObjectNode) changed.path("equipmentRuntime").path("items").get(0)).put("runningHours", "错误的字符串");
        assertThatThrownBy(() -> validate(path, "get", 200, changed.toString())).isInstanceOf(AssertionError.class);
        ObjectNode missing = (ObjectNode) mapper.readTree(response);
        ((ObjectNode) missing.path("kpis").path("patrolMileage")).remove("value");
        assertThatThrownBy(() -> validate(path, "get", 200, missing.toString())).isInstanceOf(AssertionError.class);
    }

    @Test
    void validatesFixedCameraSummaryWithoutNameOrCredentials() throws Exception {
        when(center.taskWorkflowPlanFixedCameras("task-1"))
                .thenReturn(List.of(Map.of("cameraId", "camera-1", "subStreamUrl", "present")));
        String template = "/api/bigscreen/panorama/tasks/{taskId}/fixed-cameras";
        String response = mvc.perform(get(template, "task-1")
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        validate(template, "get", 200, response);
        JsonNode camera = mapper.readTree(response).path("items").get(0);
        assertThat(camera.path("name").isNull()).isTrue();
        assertThat(camera.path("defaultQuality").asText()).isEqualTo("sub");
        assertThat(camera.has("subStreamUrl")).isFalse();
    }

    @Test
    void protectsBrowserRoutesAndDescribesBadRequests() throws Exception {
        mvc.perform(get("/api/bigscreen/panorama/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bigscreen/statistics/overview").with(jwt().jwt(token -> token.claim("azp", "field-app"))))
                .andExpect(status().isForbidden());
        String template = "/api/bigscreen/panorama/alarms/{alarmId}/handled";
        String response = mvc.perform(post(template.replace("{alarmId}", "alarm-1"))
                        .with(jwt().jwt(token -> token.claim("azp", "bigscreen-web")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disposalStatus\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        validate(template, "post", 400, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", ""})
    void describesFrameworkErrorsForMalformedOrMissingRequestBody(String body) throws Exception {
        when(jwtDecoder.decode("contract-test-token")).thenReturn(Jwt.withTokenValue("contract-test-token")
                .header("alg", "RS256").subject("user-1").claim("azp", "bigscreen-web").build());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("contract-test-token");
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String action : List.of("handled", "handle-and-continue")) {
            String template = "/api/bigscreen/panorama/alarms/{alarmId}/" + action;
            String url = template.replace("{alarmId}", "alarm-1");
            // 使用真实 HTTP 触发容器错误分派，MockMvc 不会自动执行该框架响应链路。
            var response = http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            validate(template, "post", 400, response.getBody());
            ObjectNode actual = (ObjectNode) mapper.readTree(response.getBody());
            assertThat(actual.path("status").asInt()).isEqualTo(400);
            assertThat(actual.path("path").asText()).isEqualTo(url);
            ObjectNode missing = actual.deepCopy();
            missing.remove("error");
            assertThatThrownBy(() -> validate(template, "post", 400, missing.toString())).isInstanceOf(AssertionError.class);
            ObjectNode invalid = actual.deepCopy().put("status", "错误的字符串");
            assertThatThrownBy(() -> validate(template, "post", 400, invalid.toString())).isInstanceOf(AssertionError.class);
        }
    }

    /**
     * 通过真实 HTTP 和安全过滤链触发统计拒绝路径，校验 JSON 错误正文及缺字段、错误类型反例。
     *
     * @param stopped 为 true 时关闭执行器，否则占满线程及队列；每个场景结束后重建测试上下文
     * @throws Exception 等待执行器就绪或解析响应失败时抛出
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void describesStatisticsErrorsWhenExecutorIsBusyOrStopped(boolean stopped) throws Exception {
        when(jwtDecoder.decode("contract-test-token")).thenReturn(Jwt.withTokenValue("contract-test-token")
                .header("alg", "RS256").subject("user-1").claim("azp", "bigscreen-web").build());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("contract-test-token");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(statistics, "ioExecutor");
        CountDownLatch release = new CountDownLatch(1);
        try {
            if (stopped) {
                statistics.shutdown();
            } else {
                // 等待所有工作线程阻塞后填满有界队列，确定触发实际执行器的拒绝路径。
                CountDownLatch entered = new CountDownLatch(executor.getMaximumPoolSize());
                for (int index = 0; index < executor.getMaximumPoolSize(); index++) {
                    executor.execute(() -> {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                while (executor.getQueue().remainingCapacity() > 0) {
                    executor.execute(() -> { });
                }
            }
            for (String suffix : List.of("overview", "reports/export")) {
                String path = "/api/bigscreen/statistics/" + suffix;
                HttpMethod method = "overview".equals(suffix) ? HttpMethod.GET : HttpMethod.POST;
                var response = http.exchange(path, method, new HttpEntity<>("{}", headers), String.class);
                assertThat(response.getStatusCode().value()).isEqualTo(503);
                assertThat(response.getHeaders().getContentType()).isNotNull();
                assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
                validate(path, method.name().toLowerCase(java.util.Locale.ROOT), 503, response.getBody());
                ObjectNode actual = (ObjectNode) mapper.readTree(response.getBody());
                assertThat(actual.path("status").asInt()).isEqualTo(503);
                assertThat(actual.path("path").asText()).isEqualTo(path);
                ObjectNode missing = actual.deepCopy();
                missing.remove("error");
                assertThatThrownBy(() -> validate(path, method.name().toLowerCase(java.util.Locale.ROOT), 503,
                        missing.toString())).isInstanceOf(AssertionError.class);
                ObjectNode invalid = actual.deepCopy().put("status", "错误的字符串");
                assertThatThrownBy(() -> validate(path, method.name().toLowerCase(java.util.Locale.ROOT), 503,
                        invalid.toString())).isInstanceOf(AssertionError.class);
            }
        } finally {
            release.countDown();
        }
    }

    private void validate(String path, String method, int status, String response) throws Exception {
        ObjectNode schema = contract.path("paths").path(path).path(method).path("responses").path(String.valueOf(status))
                .path("content").path("application/json").path("schema").deepCopy();
        schema.set("$defs", contract.path("components").path("schemas"));
        JsonNode jsonSchema = mapper.readTree(schema.toString().replace("#/components/schemas/", "#/$defs/"));
        assertThat(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(jsonSchema)
                .validate(mapper.readTree(response))).as("%s %s", path, response).isEmpty();
    }

    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            node.properties().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> result.set(entry.getKey(), sorted(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return node;
    }

    /** 实际自有 Controller、服务和安全过滤链，外部依赖通过替身隔离。 */
    @Configuration
    @EnableAutoConfiguration
    @Import({OpenApiConfig.class, SecurityConfig.class, BigscreenProxyController.class, BusinessTaskProxyController.class,
            PanoramaController.class, PanoramaService.class, StatisticsController.class, StatisticsService.class})
    static class Application {
    }
}
