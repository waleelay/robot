package com.robot.control.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.fixedcamera.FixedCameraPublisherLifecycleService;
import com.robot.media.common.video.VideoPublisherMode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InternalFixedCameraPublisherControllerTest {

    private final FixedCameraPublisherLifecycleService service = mock(FixedCameraPublisherLifecycleService.class);
    private final InternalFixedCameraPublisherController controller =
            new InternalFixedCameraPublisherController(service);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void allowsTrustedManagementService() {
        when(request.getHeader("X-Internal-Caller")).thenReturn("management-service");

        controller.switchMode(
                "camera-001",
                new InternalFixedCameraPublisherController.ModeSwitchRequest(
                        VideoPublisherMode.LIVEKIT_INGRESS, 7L),
                request);

        verify(service).switchMode("camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 7L);
    }

    @Test
    void rejectsUntrustedCaller() {
        when(request.getHeader("X-Internal-Caller")).thenReturn("unknown-service");

        assertThatThrownBy(() -> controller.quiesce(
                        "camera-001",
                        new InternalFixedCameraPublisherController.QuiesceRequest(7L, "DELETE"),
                        request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }
}
