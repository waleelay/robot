package com.robot.control.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.robot.control.mileage.MileageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 验证未显式开启时默认里程与文件分组文档均关闭。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = FileOpenApiContractTest.Application.class)
class FileOpenApiDisabledTest {
    @Autowired private TestRestTemplate http;
    @MockitoBean private MileageService mileage;

    @Test
    void bothDocumentsAreDisabledByDefault() {
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/v3/api-docs/files", String.class).getStatusCode().value()).isEqualTo(404);
    }
}
