package com.robot.bigscreen.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
                new ObjectMapper(), centerClient, new DeviceStatusSampler(new ObjectMapper(), centerClient, tempDir.resolve("sampler").toString(), 7), tempDir.toString());

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
        when(centerClient.mileageSummary(
                        "2026-07-01 00:00:00", "2026-07-31 23:59:59", List.of("robot-001")))
                .thenReturn(Map.of("hasData", true, "totalMeters", 12_000));
        when(centerClient.mileageSummary(
                        "2026-05-31 00:00:00", "2026-06-30 23:59:59", List.of("robot-001")))
                .thenReturn(Map.of("hasData", true, "totalMeters", 8_000));
        DeviceStatusSampler sampler = mock(DeviceStatusSampler.class);
        when(sampler.countsInRange(any(), any(), any())).thenReturn(new long[]{8, 2, 0, 7200});
        StatisticsService service = new StatisticsService(new ObjectMapper(), centerClient, sampler, tempDir.toString());

        Map<String, Object> overview = service.overview(
                "custom", "2026-07-01 00:00:00", "2026-07-31 23:59:59", "WHEELED_ROBOT", null);

        Map<String, Object> kpis = map(overview.get("kpis"));
        assertEquals(2, map(kpis.get("taskTotal")).get("value"));
        assertEquals(2, map(kpis.get("aiAlarmTotal")).get("value"));
        assertEquals(12.0, map(kpis.get("patrolMileage")).get("value"));
        assertEquals(50.0, map(kpis.get("patrolMileage")).get("compareRate"));
        Map<String, Object> runtime = map(overview.get("equipmentRuntime"));
        assertEquals(100L, runtime.get("onlineRate"));
        assertEquals(50.0, runtime.get("taskCompletionRate"));
        List<?> runtimeItems = list(runtime.get("items"));
        assertEquals(1, runtimeItems.size());
        assertEquals("WHEELED_ROBOT", map(runtimeItems.get(0)).get("deviceType"));
        assertEquals(1.6, map(runtimeItems.get(0)).get("runningHours"));
        assertEquals(0.4, map(runtimeItems.get(0)).get("offlineHours"));
        assertEquals(0.0, map(runtimeItems.get(0)).get("faultHours"));
        assertEquals(List.of(
                        Map.of("name", "火灾告警", "count", 2L, "percent", 100.0)),
                list(map(overview.get("aiAlarmAnalysis")).get("alarmTypeRanking")));
        assertEquals("A区", map(list(overview.get("alarmAreaRanking")).get(0)).get("areaName"));
        List<?> taskItems = list(map(overview.get("taskCompletion")).get("items"));
        assertEquals(1L, map(taskItems.get(0)).get("count"));
        assertEquals(1L, map(taskItems.get(3)).get("count"));
    }

    @Test
    void customRangeWithNonPaddedDatesFiltersInsteadOfReturningAllData() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.deviceTypeOptions()).thenReturn(List.of());
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "robot-001", "deviceType", "WHEELED_ROBOT"),
                Map.of("serialNumber", "robot-002", "deviceType", "ROBOT_DOG")));
        when(centerClient.realtimeStatuses(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(Map.of("serialNumber", "robot-001", "onlineStatus", "online")));
        when(centerClient.taskWorkflowInstancesForStatistics()).thenReturn(List.of(
                task("COMPLETED", "2026-07-03T10:00:00", "robot-001", 3600),
                task("FAILED", "2026-07-04T10:00:00", "robot-001", 1800),
                task("RUNNING", "2026-07-05T10:00:00", "robot-002", 600),
                task("COMPLETED", "2026-06-30T10:00:00", "robot-001", 3600)));
        when(centerClient.alarmsForStatistics(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        alarm("robot-001", "FIRE", "IMMEDIATE_DISPOSAL", "2026-07-03T11:00:00", "A区"),
                        alarm("robot-001", "FIRE", "FALSE_ALARM", "2026-07-03T12:00:00", "A区"),
                        alarm("robot-002", "SMOKE", null, "2026-07-04T12:00:00", "B区")));
        when(centerClient.mileageSummary(
                        "2026-07-01 00:00:00", "2026-07-31 23:59:59", List.of("robot-001")))
                .thenReturn(Map.of("hasData", true, "totalMeters", 12_000));
        when(centerClient.mileageSummary(
                        "2026-05-31 00:00:00", "2026-06-30 23:59:59", List.of("robot-001")))
                .thenReturn(Map.of("hasData", true, "totalMeters", 8_000));
        StatisticsService service = new StatisticsService(new ObjectMapper(), centerClient, new DeviceStatusSampler(new ObjectMapper(), centerClient, tempDir.resolve("sampler").toString(), 7), tempDir.toString());

        Map<String, Object> overview = service.overview(
                "custom", "2026-7-1 00:00:00", "2026-7-31 23:59:59", "WHEELED_ROBOT", null);

        Map<String, Object> kpis = map(overview.get("kpis"));
        assertEquals(2, map(kpis.get("taskTotal")).get("value"));
        assertEquals(2, map(kpis.get("aiAlarmTotal")).get("value"));
        assertEquals(12.0, map(kpis.get("patrolMileage")).get("value"));
    }

    @Test
    void generatesFormalReportWithDynamicSectionsAndFriendlyEmptyValues() throws Exception {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.deviceTypeOptions()).thenReturn(List.of());
        when(centerClient.devices()).thenReturn(List.of());
        when(centerClient.taskWorkflowInstancesForStatistics()).thenReturn(List.of());
        when(centerClient.alarmsForStatistics(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        StatisticsService service = new StatisticsService(new ObjectMapper(), centerClient, new DeviceStatusSampler(new ObjectMapper(), centerClient, tempDir.resolve("sampler").toString(), 7), tempDir.toString());

        StatisticsService.ReportFile report = service.createReport(Map.of(
                "modules", List.of("equipmentRuntime"),
                "timeRange", Map.of("type", "all"),
                "deviceType", "all"));
        String text;
        try (var document = Loader.loadPDF(report.bytes())) {
            text = new PDFTextStripper().getText(document);
        }

        assertTrue(report.filename().startsWith("具身智能平台统计报告-全部-全部-"));
        assertTrue(text.contains("具身智能平台统计报告"));
        assertTrue(text.contains("三、装备运行时长"));
        assertTrue(text.contains("四、报告说明"));
        assertTrue(text.contains("暂无数据"));
        assertFalse(text.contains("七、报告说明"));
        assertFalse(text.contains("null"));
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
