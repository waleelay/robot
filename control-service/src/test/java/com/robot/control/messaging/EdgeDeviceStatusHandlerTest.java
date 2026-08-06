package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EdgeDeviceStatusHandlerTest {

    private final EquipmentControlService equipmentControlService = mock(EquipmentControlService.class);
    private final RobotRegistryService robotRegistryService = mock(RobotRegistryService.class);
    private final EdgeDeviceStatusHandler handler = new EdgeDeviceStatusHandler(
            new ObjectMapper(), equipmentControlService, robotRegistryService);

    @Test
    void mapsRealEdgeStatusPayloadToUnifiedRobotState() {
        when(equipmentControlService.mergeEdgeDeviceStatus(eq("test115"), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        handler.handle("eiop/v1/edge/test115/status", """
                {
                  "messageId":"msg-status-1",
                  "messageType":"DEVICE_STATUS_REPORT",
                  "schemaVersion":"1.0",
                  "timestamp":"2026-08-05T17:07:43+08:00",
                  "payload":{"status":{
                    "basic":{"runningStatus":"待机","healthStatus":"异常"},
                    "motion":{"moving":false,"speed":0.6,"speedUnit":"m/s"},
                    "localization":{"localized":true,"coordinateType":"地图坐标","mapId":"2077",
                      "coordinateX":5.28,"coordinateY":1.37,"coordinateZ":0,"yaw":-2.87},
                    "energy":{"batteryPercent":47,"chargingStatus":"未充电"},
                    "control":{"controlMode":"导航模式","emergencyStop":false,"softStop":true},
                    "task":{"taskStatus":"已完成","progressPercent":100},
                    "rawStatus":{"network_connected":true}
                  }}
                }
                """);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(equipmentControlService).mergeEdgeDeviceStatus(eq("test115"), captor.capture());
        Map<String, Object> state = captor.getValue();
        assertThat(state)
                .containsEntry("robotId", "test115")
                .containsEntry("status", "online")
                .containsEntry("battery", 47)
                .containsEntry("speed", 0.6)
                .containsEntry("controlMode", "NAVIGATION")
                .containsEntry("missionStatus", "COMPLETED")
                .containsEntry("softStopActive", true)
                .containsEntry("timestamp", "2026-08-05 17:07:43");
        assertThat(map(state.get("location")))
                .containsEntry("x", 5.28)
                .containsEntry("y", 1.37)
                .containsEntry("z", 0)
                .containsEntry("yaw", -2.87)
                .containsEntry("mapId", "2077")
                .containsEntry("localized", true);
        assertThat(map(state.get("edgeStatus"))).containsKeys(
                "basic", "motion", "localization", "energy", "control", "task", "rawStatus");
        verify(robotRegistryService).update(state);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
