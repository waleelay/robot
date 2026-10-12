package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.fixedcamera.FixedCameraHealthService;
import com.robot.control.trajectory.TrajectoryCoordinator;
import org.junit.jupiter.api.Test;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;

/**
 * 验证媒体客户端在线转换、保留消息拒绝以及任务进度到轨迹协调器的转发。
 */
class RobotMediaStatusSubscriberTest {

    /**
     * 用受控并发验证超时提交结束前心跳不能更新在线转换表，恢复后只触发一次媒体重启。
     *
     * @throws Exception 并发任务失败或等待超时
     */
    @Test
    void heartbeatCannotInterleaveWithTimeoutTransition() throws Exception {
        EquipmentControlService control = mock(EquipmentControlService.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(new ControlServiceProperties(),
                new ObjectMapper(), media, mock(RobotMediaCommandService.class), control, registry,
                mock(IntercomCallService.class), mock(EdgeDeviceStatusHandler.class),
                mock(FixedCameraHealthService.class), mock(TrajectoryCoordinator.class));
        Map<String, Object> expired = Map.of("robotId", "study", "deviceRuntime",
                Map.of("epoch", "one", "revision", 2, "clientOnline", false, "devices", List.of()));
        Map<String, Object> recovered = Map.of("robotId", "study", "deviceRuntime",
                Map.of("epoch", "one", "revision", 3, "clientOnline", true, "devices", List.of()));
        CountDownLatch timeoutEntered = new CountDownLatch(1);
        CountDownLatch finishTimeout = new CountDownLatch(1);
        CountDownLatch heartbeatEntered = new CountDownLatch(1);
        when(control.expireStaleClientStates(any())).thenAnswer(invocation -> {
            timeoutEntered.countDown();
            assertThat(finishTimeout.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(expired);
        });
        doAnswer(invocation -> { heartbeatEntered.countDown(); return recovered; }).when(control).handleClientState(any());
        when(media.onlineRestartCommands("study", "online")).thenReturn(List.of());
        subscriber.mediaClientBecameOnline("study", "online");
        MqttMessage heartbeat = new MqttMessage("{\"robotId\":\"study\",\"status\":\"online\"}".getBytes());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> timeout = executor.submit(subscriber::expireStaleMediaClients);
            assertThat(timeoutEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> recovery = executor.submit(() -> subscriber.handleClientStatus("robot/study/media/client/status", heartbeat));
            // 超时生成与在线标志提交属于同一次转换，新心跳必须等该转换完成。
            assertThatThrownBy(() -> recovery.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(heartbeatEntered.getCount()).isEqualTo(1);
            finishTimeout.countDown();
            timeout.get(5, TimeUnit.SECONDS);
            recovery.get(5, TimeUnit.SECONDS);
            subscriber.handleClientStatus("robot/study/media/client/status", heartbeat);
            verify(media, times(1)).onlineRestartCommands("study", "online");
            assertThat(subscriber.mediaClientBecameOnline("study", "online")).isFalse();
        } finally {
            finishTimeout.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void publishesExpiredMediaClientRuntimeAndAllowsNextOnlineRecovery() {
        EquipmentControlService control = mock(EquipmentControlService.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        Map<String, Object> expired = Map.of("robotId", "robot-1", "deviceRuntime",
                Map.of("revision", 2L, "clientOnline", false, "devices", List.of()));
        when(control.expireStaleClientStates(Duration.ofSeconds(30))).thenReturn(List.of(expired));
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(
                new ControlServiceProperties(), new ObjectMapper(), mock(ControlMediaServiceClient.class),
                mock(RobotMediaCommandService.class), control, registry, mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class), mock(FixedCameraHealthService.class),
                mock(TrajectoryCoordinator.class));
        subscriber.mediaClientBecameOnline("robot-1", "online");

        subscriber.expireStaleMediaClients();

        verify(registry).update(expired);
        assertThat(subscriber.mediaClientBecameOnline("robot-1", "online")).isTrue();
    }

    @Test
    void consumesFixedSourceMetadataWithoutChangingMediaHttpStatusModel() {
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(new ControlServiceProperties(),
                new ObjectMapper().findAndRegisterModules(), media, mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class), mock(RobotRegistryService.class), mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class), mock(FixedCameraHealthService.class), mock(TrajectoryCoordinator.class));
        subscriber.handleVideoStatus("gateway/media/default/video/status", new MqttMessage("""
                {"sourceType":"FIXED_CAMERA","sourceId":"camera-001","deviceId":"camera-001",
                 "sessionId":"vs-001","status":"streaming","timestamp":"2026-10-10T10:00:00Z"}
                """.getBytes()));
        org.mockito.ArgumentCaptor<com.robot.media.common.video.VideoStatusMessage> captor =
                org.mockito.ArgumentCaptor.forClass(com.robot.media.common.video.VideoStatusMessage.class);
        verify(media).updateVideoStatus(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("vs-001");
        assertThat(captor.getValue().getStatus()).isEqualTo("streaming");
    }

    @Test
    void rejectsForeignGatewayAndRetainedVideoStatus() {
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(new ControlServiceProperties(),
                new ObjectMapper().findAndRegisterModules(), media, mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class), mock(RobotRegistryService.class), mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class), mock(FixedCameraHealthService.class), mock(TrajectoryCoordinator.class));
        MqttMessage message = new MqttMessage("""
                {"sourceType":"FIXED_CAMERA","sourceId":"camera-001","deviceId":"camera-001","sessionId":"vs-001","status":"streaming"}
                """.getBytes());
        subscriber.handleVideoStatus("gateway/media/another-gateway/video/status", message);
        message.setRetained(true);
        subscriber.handleVideoStatus("gateway/media/default/video/status", message);
        verifyNoInteractions(media);
    }

    @Test
    void tracksMediaClientOnlineTransitionIndependentlyFromRobotOnlineStatus() {
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(
                new ControlServiceProperties(),
                new ObjectMapper(),
                mock(ControlMediaServiceClient.class),
                mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class),
                mock(RobotRegistryService.class),
                mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class),
                mock(FixedCameraHealthService.class),
                mock(TrajectoryCoordinator.class));

        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isTrue();
        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isFalse();
        assertThat(subscriber.mediaClientBecameOnline("study", "offline")).isFalse();
        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isTrue();
    }

    @Test
    void retainedEdgeStatusDoesNotRefreshRobotOnlineState() {
        EdgeDeviceStatusHandler edgeHandler = mock(EdgeDeviceStatusHandler.class);
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(
                new ControlServiceProperties(),
                new ObjectMapper(),
                mock(ControlMediaServiceClient.class),
                mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class),
                mock(RobotRegistryService.class),
                mock(IntercomCallService.class),
                edgeHandler,
                mock(FixedCameraHealthService.class),
                mock(TrajectoryCoordinator.class));
        MqttMessage message = new MqttMessage("{}".getBytes());
        message.setRetained(true);

        subscriber.handleEdgeDeviceStatus("eiop/v1/edge/study/status", message);

        verifyNoInteractions(edgeHandler);
    }

    @Test
    void forwardsTaskInstanceIdFromTaskProgressToTrajectoryCoordinator() {
        TrajectoryCoordinator trajectoryCoordinator = mock(TrajectoryCoordinator.class);
        RobotMediaStatusSubscriber subscriber = subscriber(trajectoryCoordinator);
        MqttMessage message = new MqttMessage("""
                {
                  "messageType":"TASK_PROGRESS_REPORT",
                  "payload":{"status":"RUNNING","taskInstanceId":2099000870253449217}
                }
                """.getBytes());

        subscriber.handleEdgeTaskProgress("eiop/v1/edge/sx-songling-001/tasks/progress", message);

        verify(trajectoryCoordinator).observeTaskInstance("sx-songling-001", 2099000870253449217L);
    }

    @Test
    void ignoresRetainedOrIncompleteTaskProgress() {
        TrajectoryCoordinator trajectoryCoordinator = mock(TrajectoryCoordinator.class);
        RobotMediaStatusSubscriber subscriber = subscriber(trajectoryCoordinator);
        MqttMessage retained = new MqttMessage("""
                {"messageType":"TASK_PROGRESS_REPORT","payload":{"taskInstanceId":42}}
                """.getBytes());
        retained.setRetained(true);

        subscriber.handleEdgeTaskProgress("eiop/v1/edge/robot-1/tasks/progress", retained);
        subscriber.handleEdgeTaskProgress("eiop/v1/edge/robot-1/tasks/progress",
                new MqttMessage("{\"messageType\":\"TASK_PROGRESS_REPORT\",\"payload\":{}}".getBytes()));

        verifyNoInteractions(trajectoryCoordinator);
    }

    private RobotMediaStatusSubscriber subscriber(TrajectoryCoordinator trajectoryCoordinator) {
        return new RobotMediaStatusSubscriber(
                new ControlServiceProperties(),
                new ObjectMapper(),
                mock(ControlMediaServiceClient.class),
                mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class),
                mock(RobotRegistryService.class),
                mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class),
                mock(FixedCameraHealthService.class),
                trajectoryCoordinator);
    }
}
