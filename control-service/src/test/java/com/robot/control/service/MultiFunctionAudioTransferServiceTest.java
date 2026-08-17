package com.robot.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.media.common.file.FileListItemResponse;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MultiFunctionAudioTransferServiceTest {

    private final ControlMediaServiceClient mediaServiceClient =
            org.mockito.Mockito.mock(ControlMediaServiceClient.class);
    private final EquipmentControlService equipmentControlService =
            org.mockito.Mockito.mock(EquipmentControlService.class);
    private final CurrentUser user =
            new CurrentUser("operator-1", "org001", Set.of("EQUIPMENT_OPERATOR"), "terminal-1");
    private final MultiFunctionAudioTransferService service =
            new MultiFunctionAudioTransferService(mediaServiceClient, equipmentControlService);

    @Test
    void publishesReadyMediaFileToRobotClient() {
        when(mediaServiceClient.file("file-001", user)).thenReturn(audioFile(
                "file-001", "robot-001", "broadcaster-001", "notice.mp3", 10, "READY"));
        when(equipmentControlService.publishMultiFunctionAudioTransfer(
                eq("robot-001"), eq("broadcaster-001"), any()))
                .thenReturn(Map.of("commandId", "cmd-001", "status", "PUBLISHED"));

        Map<String, Object> response = service.transfer(
                "robot-001",
                "broadcaster-001",
                Map.of("fileId", "file-001"),
                user);

        assertThat(response)
                .containsEntry("status", "PUBLISHED")
                .containsEntry("commandId", "cmd-001")
                .containsEntry("fileId", "file-001")
                .containsEntry("fileName", "notice.mp3")
                .containsEntry("fileSize", 10L)
                .containsKey("transferId");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(equipmentControlService).publishMultiFunctionAudioTransfer(
                eq("robot-001"), eq("broadcaster-001"), params.capture());
        assertThat(params.getValue())
                .containsEntry("fileId", "file-001")
                .containsEntry("fileName", "notice.mp3")
                .containsEntry("fileSize", 10L)
                .containsEntry("orgId", "org001")
                .containsKey("transferId")
                .doesNotContainKeys("downloadUrl", "sha256", "expireAt");
    }

    @Test
    void rejectsFileThatDoesNotBelongToTargetDevice() {
        when(mediaServiceClient.file("file-001", user)).thenReturn(audioFile(
                "file-001", "robot-001", "other-device", "notice.mp3", 10, "READY"));

        assertThatThrownBy(() -> service.transfer(
                "robot-001",
                "broadcaster-001",
                Map.of("fileId", "file-001"),
                user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于目标机器人或设备");
        verify(equipmentControlService, never()).publishMultiFunctionAudioTransfer(any(), any(), any());
    }

    private FileListItemResponse audioFile(
            String fileId,
            String robotId,
            String deviceId,
            String fileName,
            long fileSize,
            String status) {
        return new FileListItemResponse(
                fileId,
                robotId,
                deviceId,
                null,
                "AUDIO",
                fileName,
                "audio/mpeg",
                fileSize,
                null,
                null,
                null,
                null,
                null,
                status,
                null,
                null,
                null,
                null,
                null);
    }
}
