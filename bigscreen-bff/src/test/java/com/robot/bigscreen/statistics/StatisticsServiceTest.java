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
        when(centerClient.devices()).thenReturn(List.of());
        when(centerClient.taskWorkflowInstancesForStatistics()).thenReturn(List.of());
        when(centerClient.alarmsForStatistics(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        StatisticsService service = new StatisticsService(
                new ObjectMapper(), centerClient, tempDir.toString());

        Map<String, Object> overview = service.overview("month", null, null, "all", null);

        assertEquals(List.of(
                Map.of("value", "all", "label", "全部"),
                Map.of("value", "WHEELED_ROBOT", "label", "轮式巡检车"),
                Map.of("value", "ROBOT_DOG", "label", "四足机器人"),
                Map.of("value", "FIXED_CAMERA", "label", "固定摄像头")), overview.get("deviceTypeOptions"));
    }

    @Test
    void aggregatesRealTaskAlarmAndOnlineStatistics() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.deviceTypeOptions()).thenReturn(List.of());
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "robot-001", "deviceType", "WHEELED_ROBOT"),
                Map.of("serialNumber", "robot-002", "deviceType", "ROBOT_DOG")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                Map.of("serialNumber", "robot-001", "onlineStatus", "online")));
        when(centerClient.taskWorkflowInstancesForStatistics()).thenReturn(List.of(
                task("COMPLETED", "2026-07-03T10:00:00", "robot-001", 3600),
                task("FAILED", "2026-07-04T10:00:00", "robot-001", 1800),
                task("RUNNING", "2026-07-05T10:00:00", "robot-002", 600),
                task("COMPLETED", "2026-06-30T10:00:00", "robot-001", 3600)));
        when(centerClient.alarmsForStatistics("2026-07-01 00:00:00", "2026-07-31 23:59:59")).thenReturn(List.of(
                alarm("robot-001", "FIRE", "IMMEDIATE_DISPOSAL", "2026-07-03T11:00:00", "A区"),
                alarm("robot-001", "FIRE", "FALSE_ALARM", "2026-07-03T12:00:00", "A区"),
                alarm("robot-002", "SMOKE", null, "2026-07-04T12:00:00", "B区")));
        StatisticsService service = new StatisticsService(new ObjectMapper(), centerClient, tempDir.toString());

        Map<String, Object> overview = service.overview(
                "custom", "2026-07-01 00:00:00", "2026-07-31 23:59:59", "WHEELED_ROBOT", null);

        Map<String, Object> kpis = map(overview.get("kpis"));
        assertEquals(2, map(kpis.get("taskTotal")).get("value"));
        assertEquals(2, map(kpis.get("aiAlarmTotal")).get("value"));
        Map<String, Object> runtime = map(overview.get("equipmentRuntime"));
        assertEquals(100L, runtime.get("onlineRate"));
        assertEquals(50.0, runtime.get("taskCompletionRate"));
        assertEquals(1.5, map(list(runtime.get("items")).get(0)).get("runningHours"));
        assertEquals(List.of(
                        Map.of("name", "火灾告警", "count", 2L, "percent", 100.0)),
                list(map(overview.get("aiAlarmAnalysis")).get("alarmTypeRanking")));
        assertEquals("A区", map(list(overview.get("alarmAreaRanking")).get(0)).get("areaName"));
        List<?> taskItems = list(map(overview.get("taskCompletion")).get("items"));
        assertEquals(1L, map(taskItems.get(0)).get("count"));
        assertEquals(1L, map(taskItems.get(3)).get("count"));
    }

    private Map<String, Object> task(String status, String startedAt, String serialNumber, int durationSeconds) {
        return Map.of(
                "status", status,
                "startedAt", startedAt,
                "durationSeconds", durationSeconds,
                "deviceSummaries", List.of(Map.of("serialNumber", serialNumber)));
    }

    private Map<String, Object> alarm(
            String serialNumber,
            String alarmType,
            String handleResult,
            String occurredAt,
            String areaName) {
        java.util.LinkedHashMap<String, Object> alarm = new java.util.LinkedHashMap<>();
        alarm.put("serialNumber", serialNumber);
        alarm.put("alarmType", alarmType);
        alarm.put("handleResult", handleResult);
        alarm.put("occurredAt", occurredAt);
        alarm.put("rawPayload", Map.of("location", Map.of("address", areaName)));
        return alarm;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
