package com.robot.bigscreen.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatisticsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsManagementDeviceTypesAndAppendsFixedCamera() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.deviceTypeOptions()).thenReturn(List.of(
                Map.of("value", "WHEELED_ROBOT", "label", "轮式巡检车"),
                Map.of("itemCode", "ROBOT_DOG", "itemName", "四足机器人")));
        StatisticsService service = new StatisticsService(
                new ObjectMapper(), centerClient, tempDir.toString());

        Map<String, Object> overview = service.overview("month", null, null, "all", null);

        assertEquals(List.of(
                Map.of("value", "all", "label", "全部"),
                Map.of("value", "WHEELED_ROBOT", "label", "轮式巡检车"),
                Map.of("value", "ROBOT_DOG", "label", "四足机器人"),
                Map.of("value", "FIXED_CAMERA", "label", "固定摄像头")), overview.get("deviceTypeOptions"));
    }
}
