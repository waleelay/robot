package com.robot.control.mileage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.robot.control.api.ApiExceptionHandler;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.config.MileageOpenApiConfig;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 使用真实 HTTP、H2 查询和生成契约验证响应，避免仅检查注解。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = MileageOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=true", "spring.main.banner-mode=off"})
class MileageOpenApiContractTest {

    private static final String PATH = "/api/control/statistics/mileage";
    private static final String QUERY = "?startTime=2026-08-14T00:00:00&endTime=2026-08-14T23:59:59";
    private static final Path CONTRACT_DIRECTORY = Path.of("../quality/openapi");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MileageService service;

    private JsonNode contract;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM control_device_mileage_bucket");
        jdbc.update("DELETE FROM control_device_mileage_checkpoint");
        ResponseEntity<String> response = http.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        contract = mapper.readTree(response.getBody());
    }

    @Test
    void exportsOnlyThePilotContractAndDetectsDrift() throws Exception {
        assertThat(contract.path("openapi").asText()).isEqualTo("3.1.0");
        assertThat(contract.path("paths").size()).isEqualTo(1);
        JsonNode operation = contract.path("paths").path(PATH).path("get");
        assertThat(operation.path("operationId").asText()).isEqualTo("queryMileageSummary");
        for (JsonNode parameter : operation.path("parameters")) {
            if (List.of("startTime", "endTime").contains(parameter.path("name").asText())) {
                assertThat(parameter.path("required").asBoolean()).isTrue();
                assertThat(parameter.path("schema").path("type").asText()).isEqualTo("string");
                assertThat(parameter.path("schema").path("format").asText()).isNotEqualTo("date-time");
            }
        }
        Path generated = Path.of("target/openapi/control-mileage.json");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(contract)) + "\n");
        snapshot(CONTRACT_DIRECTORY.resolve("control-mileage.json"), contract);
    }

    @Test
    void preservesNullsForRequestedRobotsWithoutSamplesAndDoesNotRequireIdentityHeaders() throws Exception {
        JsonNode body = request(QUERY + "&robotIds=robot-001&robotIds=robot-001&robotIds=robot-empty", 200);
        assertThat(body.path("hasData").asBoolean()).isFalse();
        assertThat(body.has("totalMeters")).isTrue();
        assertThat(body.get("totalMeters").isNull()).isTrue();
        assertThat(body.path("byRobot").size()).isEqualTo(2);
        assertThat(body.path("byRobot").get(0).get("mileageMeters").isNull()).isTrue();
        assertThat(body.has("quality")).isFalse();
        snapshot(CONTRACT_DIRECTORY.resolve("fixtures/mileage-no-data.json"), body);
    }

    @Test
    void returnsActualTotalsAndKeepsUnknownRobotSeparate() throws Exception {
        insert("robot-001", "12.500", 2);
        insert("robot-other", "8.000", 1);
        JsonNode body = request(QUERY + "&robotIds=robot-001&robotIds=robot-empty", 200);
        assertThat(body.path("totalMeters").decimalValue()).isEqualByComparingTo("12.500");
        assertThat(body.path("sampleCount").asLong()).isEqualTo(2L);
        assertThat(body.path("byRobot").get(1).path("hasData").asBoolean()).isFalse();
        snapshot(CONTRACT_DIRECTORY.resolve("fixtures/mileage-success.json"), body);
    }

    @Test
    void preservesMeasuredZeroAndAcceptsDisplayTimeAndCommaSeparatedIds() throws Exception {
        insert("robot-001", "0.000", 1);
        JsonNode body = request(QUERY + "&robotIds=robot-001", 200);
        assertThat(body.path("hasData").asBoolean()).isTrue();
        assertThat(body.path("totalMeters").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        snapshot(CONTRACT_DIRECTORY.resolve("fixtures/mileage-zero.json"), body);
        JsonNode display = request("?startTime=2026-08-14 00:00:00&endTime=2026-08-14 23:59:59"
                + "&robotIds=robot-001,robot-empty", 200);
        assertThat(display.path("byRobot").size()).isEqualTo(2);
        assertThat(display.path("startTime").asText()).isEqualTo("2026-08-14 00:00:00");
    }

    @Test
    void queriesAllWhenIdsAreAbsentOrBlankAndIncludesBothTimeBoundaries() throws Exception {
        insert("robot-001", "1.000", 1);
        jdbc.update("UPDATE control_device_mileage_bucket SET bucket_time = '2026-08-14 00:00:00'");
        insert("robot-002", "2.000", 1);
        jdbc.update("UPDATE control_device_mileage_bucket SET bucket_time = '2026-08-14 23:59:59' WHERE robot_id = 'robot-002'");
        assertThat(request(QUERY, 200).path("totalMeters").decimalValue()).isEqualByComparingTo("3.000");
        assertThat(request(QUERY + "&robotIds=", 200).path("byRobot").size()).isEqualTo(2);
    }

    @Test
    void documentsActualBadRequestShapes() throws Exception {
        JsonNode business = request("?startTime=2026-08-15T00:00:00&endTime=2026-08-14T00:00:00", 400);
        assertThat(business.path("code").asText()).isEqualTo("INVALID_CONTROL_REQUEST");
        for (String query : List.of("?endTime=2026-08-14T00:00:00", "?startTime=bad&endTime=2026-08-14T00:00:00",
                "?startTime=&endTime=2026-08-14T00:00:00")) {
            assertThat(request(query, 400).path("status").asInt()).isEqualTo(400);
        }
    }

    @Test
    void documentsUnhandledStorageFailure() throws Exception {
        jdbc.execute("DROP TABLE control_device_mileage_bucket");
        try {
            assertThat(request(QUERY, 500).path("status").asInt()).isEqualTo(500);
        } finally {
            service.initializeSchema();
        }
    }

    @Test
    void validatorRejectsMissingFieldsWrongTypesAndMalformedTimes() throws Exception {
        ObjectNode body = (ObjectNode) request(QUERY, 200);
        body.remove("totalMeters");
        assertThat(validate(body, 200)).isNotEmpty();
        body.put("totalMeters", "unknown");
        assertThat(validate(body, 200)).isNotEmpty();
        body.putNull("totalMeters");
        body.put("startTime", "2026-08-14T00:00:00Z");
        assertThat(validate(body, 200)).isNotEmpty();
    }

    private JsonNode request(String query, int status) throws Exception {
        ResponseEntity<String> response = http.getForEntity(PATH + query, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(validate(body, status)).isEmpty();
        return body;
    }

    private java.util.Set<com.networknt.schema.ValidationMessage> validate(JsonNode body, int status) {
        ObjectNode schema = contract.path("paths").path(PATH).path("get").path("responses")
                .path(String.valueOf(status)).path("content").path("application/json").path("schema").deepCopy();
        schema.set("$defs", contract.path("components").path("schemas"));
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(jsonSchema(schema)).validate(body);
    }

    /** 将 OAS 组件引用和示例注解映射为 JSON Schema 2020-12，保留所有验证关键字。 */
    private JsonNode jsonSchema(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            value.properties().forEach(field -> {
                if ("$ref".equals(field.getKey())) {
                    result.put("$ref", field.getValue().asText().replace("#/components/schemas/", "#/$defs/"));
                } else if ("example".equals(field.getKey())) {
                    result.set("examples", mapper.createArrayNode().add(field.getValue()));
                } else {
                    result.set(field.getKey(), jsonSchema(field.getValue()));
                }
            });
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(jsonSchema(item)));
            return result;
        }
        return value;
    }

    private void insert(String robot, String meters, long samples) {
        jdbc.update("INSERT INTO control_device_mileage_bucket "
                        + "(robot_id, bucket_time, mileage_m, sample_count, updated_at) VALUES (?, ?, ?, ?, ?)",
                robot, "2026-08-14 10:00:00", new BigDecimal(meters), samples, "2026-08-14 10:00:00");
    }

    private void snapshot(Path path, JsonNode value) throws Exception {
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(value)) + "\n");
        }
        assertThat(Files.exists(path)).as("先显式生成并审查契约/样例：-Dopenapi.update=true").isTrue();
        assertThat(value).as("契约或实际响应发生变化：%s", path).isEqualTo(mapper.readTree(path.toFile()));
    }

    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> fields = new ArrayList<>();
            value.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(name -> result.set(name, sorted(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return value;
    }

    /** 只启动 HTTP、契约生成和里程查询，不连接生产数据库、MQTT 或其他服务。 */
    @Configuration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({MileageController.class, MileageService.class, DateTimeConfig.class,
            ApiExceptionHandler.class, MileageOpenApiConfig.class})
    static class Application {
        @Bean
        DriverManagerDataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:mileage-contract;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DriverManagerDataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DriverManagerDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        MileageProperties mileageProperties() {
            return new MileageProperties();
        }

        @Bean
        MediaWebSocketPublisher publisher(ObjectMapper mapper) {
            return new MediaWebSocketPublisher(mapper);
        }
    }
}
