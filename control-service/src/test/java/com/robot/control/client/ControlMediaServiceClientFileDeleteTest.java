package com.robot.control.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.control.auth.CurrentUser;
import com.robot.control.config.ControlProperties;
import com.robot.control.dto.FileBatchDeleteRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ControlMediaServiceClientFileDeleteTest {

    private final CurrentUser user = new CurrentUser("user-1", "org001", Set.of("MEDIA_OPERATOR"), "bigscreen");
    private MockRestServiceServer server;
    private ControlMediaServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ControlProperties properties = new ControlProperties();
        properties.setMediaServiceBaseUrl("http://media-service");
        client = new ControlMediaServiceClient(properties, builder);
    }

    @Test
    void forwardsSingleDeleteToMediaService() {
        server.expect(requestTo("http://media-service/internal/media/files/file-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Org-Id", "org001"))
                .andRespond(withNoContent());

        client.deleteFile("file-1", user);

        server.verify();
    }

    @Test
    void forwardsBatchDeleteAndReturnsPerFileResults() {
        server.expect(requestTo("http://media-service/internal/media/files/batch"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Org-Id", "org001"))
                .andExpect(jsonPath("$.fileIds[0]").value("file-1"))
                .andExpect(jsonPath("$.fileIds[1]").value("file-2"))
                .andRespond(withSuccess("""
                        {
                          "total": 2,
                          "succeeded": 1,
                          "failed": 1,
                          "results": [
                            {"fileId":"file-1","success":true,"code":"DELETED","message":"删除成功"},
                            {"fileId":"file-2","success":false,"code":"FILE_NOT_FOUND","message":"未找到文件"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.deleteFiles(new FileBatchDeleteRequest(List.of("file-1", "file-2")), user);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.results()).hasSize(2);
        server.verify();
    }
}
