package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitIngressService;
import com.robot.mediaserver.livekit.LiveKitIngressService.IngressInfo;
import com.robot.mediaserver.video.model.FixedCameraIngressOperation;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.mediaserver.video.repository.FixedCameraIngressRuntimeLockRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class FixedCameraIngressServiceTest {

    private final LiveKitIngressService liveKit = mock(LiveKitIngressService.class);
    private final VideoSourceRuntimeRepository runtimeRepository = mock(VideoSourceRuntimeRepository.class);
    private final FixedCameraIngressRuntimeLockRepository lockRepository = mock(FixedCameraIngressRuntimeLockRepository.class);
    private final VideoSessionService videoSessionService = mock(VideoSessionService.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final VideoSourceRuntime runtime = runtime();
    private FixedCameraIngressService service;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties();
        properties.getLivekit().setIngressEnabled(true);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(lockRepository.lock(anyString(), any(Duration.class))).thenReturn(Optional.of(runtime));
        service = new FixedCameraIngressService(
                properties, liveKit, runtimeRepository, lockRepository, new ObjectMapper(),
                new FixedCameraIngressPersistenceExceptionClassifier(), videoSessionService, transactionManager);
    }

    @Test
    void issuesCredentialOnlyForNewlyCreatedIngress() {
        when(liveKit.list(any(Duration.class))).thenReturn(List.of());
        when(liveKit.create(any(), any(Duration.class))).thenReturn(new IngressInfo(
                "IN_001", "fixed-camera-camera-001", "rtmp://example/live", "RT_secret",
                runtime.getRoomName(), "fixed-camera:camera-001", "{}", "ENDPOINT_WAITING", null));

        var response = service.create("camera-001", 1L);

        assertThat(response.credentialIssued()).isTrue();
        assertThat(response.url()).isEqualTo("rtmp://example/live");
        assertThat(response.streamKey()).isEqualTo("RT_secret");
        assertThat(runtime.getIngressId()).isEqualTo("IN_001");
    }

    @Test
    void rejectsStaleRevisionBeforeCallingLivekit() {
        runtime.setIngressOperationRevision(2L);
        runtime.setAcceptedIngressOperation(FixedCameraIngressOperation.CREATE);

        assertThatThrownBy(() -> service.create("camera-001", 1L))
                .isInstanceOf(FixedCameraIngressException.class)
                .extracting("code")
                .isEqualTo("FIXED_CAMERA_MEDIA_OPERATION_STALE");

        verify(liveKit, never()).list(any());
        verify(liveKit, never()).create(any(), any());
    }

    @Test
    void revokeRemainsAvailableWhenIngressCreationIsDisabled() {
        MediaProperties properties = new MediaProperties();
        properties.getLivekit().setIngressEnabled(false);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(lockRepository.lock(anyString(), any(Duration.class))).thenReturn(Optional.of(runtime));
        service = new FixedCameraIngressService(
                properties, liveKit, runtimeRepository, lockRepository, new ObjectMapper(),
                new FixedCameraIngressPersistenceExceptionClassifier(), videoSessionService, transactionManager);
        when(liveKit.list(any(Duration.class))).thenReturn(List.of());

        service.revoke("camera-001", 3L);

        assertThat(runtime.getAcceptedIngressOperation()).isEqualTo(FixedCameraIngressOperation.REVOKE);
        assertThat(runtime.getIngressOperationRevision()).isEqualTo(3L);
        verify(liveKit).list(any(Duration.class));
        verify(videoSessionService).quiesceLiveKitIngress("camera-001");
    }

    @Test
    void repeatedRevokeIsSafeAfterRuntimeAlreadySwitchedToGateway() {
        runtime.setPublisherMode(VideoPublisherMode.FIXED_CAMERA_GATEWAY);
        runtime.setIngressId(null);

        service.revoke("camera-001", 4L);

        assertThat(runtime.getIngressOperationRevision()).isEqualTo(4L);
        assertThat(runtime.getAcceptedIngressOperation()).isEqualTo(FixedCameraIngressOperation.REVOKE);
        verify(liveKit, never()).list(any());
        verify(videoSessionService, never()).quiesceLiveKitIngress(anyString());
    }

    @Test
    void reconcileRestoresExactIngressMappingWithoutIssuingCredential() {
        runtime.setIngressOperationRevision(7L);
        runtime.setAcceptedIngressOperation(FixedCameraIngressOperation.CREATE);
        runtime.setIngressId(null);
        String metadata = """
                {"managedBy":"robot-mediaserver","cameraId":"camera-001",\
                "operationRevision":7,"operationType":"CREATE","createdAtEpochSeconds":4102444800}
                """;
        IngressInfo resource = new IngressInfo(
                "IN_recovered", "fixed-camera-camera-001", null, null,
                runtime.getRoomName(), "fixed-camera:camera-001", metadata, "ENDPOINT_BUFFERING", null);
        when(liveKit.list(any(Duration.class))).thenReturn(List.of(resource));
        when(runtimeRepository.findByPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS))
                .thenReturn(List.of(runtime));

        service.reconcileManagedResources();

        assertThat(runtime.getIngressId()).isEqualTo("IN_recovered");
        verify(liveKit, never()).create(any(), any());
        verify(liveKit, never()).delete(anyString(), any());
    }

    private VideoSourceRuntime runtime() {
        VideoSourceRuntime value = new VideoSourceRuntime();
        value.setRuntimeId("runtime-fixed");
        value.setSourceType(VideoSourceType.FIXED_CAMERA);
        value.setSourceId("camera-001");
        value.setDeviceId("camera-001");
        value.setChannel(VideoChannel.visible);
        value.setQuality(VideoQuality.main);
        value.setRoomName("media.fixed.camera-001.visible.main");
        value.setPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS);
        value.setCreatedAt(OffsetDateTime.now());
        value.setUpdatedAt(OffsetDateTime.now());
        return value;
    }
}
