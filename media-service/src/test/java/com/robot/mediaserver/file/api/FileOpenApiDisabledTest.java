package com.robot.mediaserver.file.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.file.service.FileMultipartCompletionService;
import com.robot.mediaserver.file.progress.FileUploadProgressService;

/** 验证文档默认关闭时文件业务配置不意外开放 API 描述端点。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = FileOpenApiContractTest.Application.class)
class FileOpenApiDisabledTest {
    @Autowired private TestRestTemplate http;
    @MockitoBean private FileService files;
    @MockitoBean private FileMultipartCompletionService completion;
    @MockitoBean private FileUploadProgressService progress;

    @Test
    void apiDocsAreDisabledByDefault() {
        assertThat(http.getForEntity("/v3/api-docs", String.class).getStatusCode().value()).isEqualTo(404);
    }
}
