package com.robot.control.mileage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** 验证显式关闭配置时不提供文档端点。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = MileageOpenApiContractTest.Application.class,
        properties = {"springdoc.api-docs.enabled=false", "spring.main.banner-mode=off"})
class MileageOpenApiDisabledTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void doesNotExposeApiDocsWhenDisabled() {
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(404);
    }
}
