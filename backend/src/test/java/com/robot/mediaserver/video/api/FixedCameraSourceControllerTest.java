package com.robot.mediaserver.video.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.media.common.video.FixedCameraPublisherModeRequest;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.mediaserver.video.service.VideoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FixedCameraSourceControllerTest {

    private final VideoSessionService service = mock(VideoSessionService.class);
    private final FixedCameraSourceController controller = new FixedCameraSourceController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void allowsTrustedControlService() {
        when(request.getHeader("X-Internal-Caller")).thenReturn("control-service");

        controller.switchPublisherMode(
                "camera-001",
                7L,
                new FixedCameraPublisherModeRequest(VideoPublisherMode.LIVEKIT_INGRESS),
                request);

        verify(service).switchFixedCameraPublisherMode(
                "camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 7L);
    }

    @Test
    void rejectsUntrustedCaller() {
        when(request.getHeader("X-Internal-Caller")).thenReturn("unknown-service");

        assertThatThrownBy(() -> controller.publisherPresence("camera-001", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
