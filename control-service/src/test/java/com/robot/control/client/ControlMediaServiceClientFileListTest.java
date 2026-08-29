package com.robot.control.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.robot.control.auth.CurrentUser;
import com.robot.control.config.ControlProperties;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ControlMediaServiceClientFileListTest {

    private final CurrentUser user = new CurrentUser("user-1", "org001", Set.of("MEDIA_VIEWER"), "bigscreen");
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
    void forwardsFileSourceBeforeMediaPagination() {
        server.expect(requestTo("http://media-service/internal/media/files?fileType=IMAGE&status=READY&source=WEB_SNAPSHOT&page=0&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Org-Id", "org001"))
                .andRespond(withSuccess("{\"items\":[],\"page\":0,\"size\":20,\"total\":0}", MediaType.APPLICATION_JSON));

        var response = client.files(
                null, null, null, FileType.IMAGE, FileStatus.READY, "WEB_SNAPSHOT", 0, 20, user);

        assertThat(response.items()).isEmpty();
        server.verify();
    }
}
