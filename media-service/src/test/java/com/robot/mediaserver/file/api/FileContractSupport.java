package com.robot.mediaserver.file.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 对 HTTP 真实序列化结果进行 Schema 校验，并显式管理契约快照。 */
final class FileContractSupport {
    private final ObjectMapper mapper;
    private final JsonNode contract;

    FileContractSupport(ObjectMapper mapper, JsonNode contract) {
        this.mapper = mapper;
        this.contract = contract;
    }

    void validate(String path, String method, int status, JsonNode body) {
        JsonNode response = contract.path("paths").path(path).path(method).path("responses").path(String.valueOf(status));
        assertThat(response.isMissingNode()).as("契约必须声明 HTTP %s %s %s", method, path, status).isFalse();
        ObjectNode schema = (ObjectNode) response.path("content").path("application/json").path("schema").deepCopy();
        schema.set("$defs", contract.path("components").path("schemas"));
        var messages = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(jsonSchema(schema)).validate(body);
        assertThat(messages).as("%s %s %s", method, path, body).isEmpty();
    }

    void validateRequest(String path, String method, JsonNode body) {
        ObjectNode schema = contract.path("paths").path(path).path(method).path("requestBody")
                .path("content").path("application/json").path("schema").deepCopy();
        schema.set("$defs", contract.path("components").path("schemas"));
        assertThat(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(jsonSchema(schema)).validate(body)).as("请求 %s %s", method, path).isEmpty();
    }

    void snapshot(String fileName, JsonNode value) throws Exception {
        Path expected = Path.of("../quality/openapi").resolve(fileName);
        Path generated = Path.of("target/openapi").resolve(fileName);
        Files.createDirectories(generated.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(value)) + "\n";
        Files.writeString(generated, json);
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(expected.getParent());
            Files.writeString(expected, json);
        }
        assertThat(expected).as("显式生成并审查快照：-Dopenapi.update=true").exists();
        assertThat(value).isEqualTo(mapper.readTree(expected.toFile()));
    }

    private JsonNode jsonSchema(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            node.properties().forEach(field -> {
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
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(item -> result.add(jsonSchema(item)));
            return result;
        }
        return node;
    }

    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sorted(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return node;
    }
}
