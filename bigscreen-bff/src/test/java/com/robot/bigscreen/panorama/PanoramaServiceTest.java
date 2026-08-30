package com.robot.bigscreen.panorama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PanoramaServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failedMapQueryMustNotBecomeEmptyOverviewAndCanRecover() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.enabledMaps())
                .thenThrow(new IllegalStateException("地图查询暂不可用"))
                .thenReturn(List.of(Map.of("id", "map-a", "mapName", "地图甲")));
        PanoramaService service = new PanoramaService(client, new ObjectMapper());

        assertThrows(IllegalStateException.class, service::overview);
        assertEquals("map-a", maps(service.overview().get("map")).get(0).get("id"));
        verify(client, times(2)).enabledMaps();
    }

    @Test
    void successfulEmptyMapQueryRemainsValidOverview() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.enabledMaps()).thenReturn(List.of());

        assertEquals(List.of(), maps(new PanoramaService(client, new ObjectMapper()).overview().get("map")));
    }

    @Test
    void doesNotShareOverviewBetweenOrganizationsWithTheSameSubject() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        PanoramaService service = new PanoramaService(client, new ObjectMapper());

        authenticate("user-001", "org-a");
        service.overview();
        authenticate("user-001", "org-b");
        service.overview();

        verify(client, times(2)).enabledMaps();
    }

    @Test
    void overviewRuntimeUsesRegistryAndDoesNotLoadComponents() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.devices()).thenReturn(List.of(Map.of("id", "101", "serialNumber", "robot-1", "model", "M1")));
        when(client.registeredRobots()).thenReturn(List.of(Map.of("robotId", "robot-1", "status", "online",
                "battery", 0, "speed", 0.0, "controlMode", "导航模式", "runtimeUpdatedAt", "2026-08-28T07:00:00.123456789Z")));
        when(client.realtimeStatuses(List.of("robot-1"))).thenReturn(List.of(Map.of("serialNumber", "robot-1",
                "status", Map.of("energy", Map.of("batteryPercent", 99), "motion", Map.of("speed", 9),
                        "control", Map.of("controlMode", "手动模式")))));
        Map<String, Object> device = map(((List<?>) new PanoramaService(client, new ObjectMapper()).overview().get("devices")).get(0));
        assertEquals(0, device.get("battery"));
        assertEquals(0.0, device.get("speed"));
        assertEquals("导航模式", device.get("controlMode"));
        assertEquals("2026-08-28T07:00:00.123456789Z", device.get("runtimeUpdatedAt"));
        assertNull(device.get("mountedDeviceCount"));
        verify(client, never()).device(anyString());
    }

    @Test
    void detailLoadsOnlyAuthorizedTargetComponentsAndExcludesBody() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.devices()).thenReturn(List.of(Map.of("id", "101", "serialNumber", "robot-1"),
                Map.of("id", "102", "serialNumber", "robot-2")));
        when(client.device("101")).thenReturn(Optional.of(Map.of("device", Map.of("model", "M2"), "components", List.of(
                Map.of("code", "body", "componentType", "BODY"),
                Map.of("code", "ptz", "componentType", "PTZ"),
                Map.of("code", "speaker", "componentType", "SPEAKER")))));
        PanoramaService service = new PanoramaService(client, new ObjectMapper());
        Map<String, Object> detail = service.deviceDetail("robot-1");
        assertEquals("M2", detail.get("model"));
        assertEquals(2, detail.get("mountedDeviceCount"));
        assertEquals(2, ((List<?>) detail.get("mountedDevices")).size());
        assertEquals(detail, service.deviceDetail("robot-1"));
        verify(client, times(1)).device("101");
        verify(client, never()).device("102");
        assertNull(service.deviceDetail("not-authorized").get("robotId"));
        verify(client, never()).device("not-authorized");
        verify(client, never()).taskWorkflowReplay(anyString());
    }

    @Test
    void missingDetailIsUnknownButExplicitEmptyComponentsMeansZero() {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.devices()).thenReturn(List.of(Map.of("id", "101", "serialNumber", "robot-1"),
                Map.of("id", "102", "serialNumber", "robot-2")));
        when(client.device("101")).thenReturn(Optional.empty());
        when(client.device("102")).thenReturn(Optional.of(Map.of("device", Map.of(), "components", List.of())));
        PanoramaService service = new PanoramaService(client, new ObjectMapper());
        assertNull(service.deviceDetail("robot-1").get("mountedDeviceCount"));
        assertEquals(0, service.deviceDetail("robot-2").get("mountedDeviceCount"));
    }

    @Test
    void concurrentDetailRequestsShareOneTargetQuery() throws Exception {
        PanoramaCenterClient client = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(client);
        when(client.devices()).thenReturn(List.of(Map.of("id", "101", "serialNumber", "robot-1")));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(client.device("101")).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
            return Optional.of(Map.of("components", List.of()));
        });
        PanoramaService service = new PanoramaService(client, new ObjectMapper());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var calls = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> service.deviceDetail("robot-1"))).toList();
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();
            for (var call : calls) assertEquals(0, call.get(3, java.util.concurrent.TimeUnit.SECONDS).get("mountedDeviceCount"));
            verify(client, times(1)).device("101");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void returnsPersistedTodayMileageInPatrolOverview() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.mileageSummary(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(List.of())))
                .thenReturn(Map.of(
                        "hasData", true,
                        "totalMeters", 1234.5,
                        "quality", "NORMAL",
                        "timezone", "Asia/Shanghai"));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        Map<String, Object> patrolOverview = map(overview.get("patrolOverview"));
        assertEquals(1.2, patrolOverview.get("mileageToday"));
        assertEquals("KM", patrolOverview.get("mileageUnit"));
        assertEquals(true, patrolOverview.get("mileageHasData"));
    }

    @Test
    void ignoresSourceRecordsWithoutDeviceIdentifiers() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of("controlMode", "AUTO")));
        when(centerClient.registeredRobots()).thenReturn(List.of(Map.of("status", "online")));
        when(centerClient.fixedCameras()).thenReturn(List.of(Map.of("enabled", true)));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        assertEquals(List.of(), maps(overview.get("devices")));
        assertEquals(0, ((Map<?, ?>) overview.get("deviceStats")).get("total"));
    }

    @Test
    void keepsMapSummaryInOverviewAndLoadsMapResourcesOnDemand() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        Map<String, Object> firstMap = Map.ofEntries(
                Map.entry("id", 2077775285125144578L),
                Map.entry("mapName", "Map One"),
                Map.entry("mapCode", "MAP-001"),
                Map.entry("mapType", "INDOOR"),
                Map.entry("regionId", 11L),
                Map.entry("fileId", 12L),
                Map.entry("fileName", "map.zip"),
                Map.entry("previewImageUrl", "/preview"),
                Map.entry("enabled", true),
                Map.entry("remark", "remark"));
        Map<String, Object> secondMap = Map.of("mapId", 2, "mapName", "Map Two");
        Map<String, Object> mapWithoutId = Map.of("mapName", "Map Without Id");
        List<Map<String, Object>> firstPoints = List.of(Map.of(
                "id", 101L,
                "pointName", "Start",
                "mapId", 2077775285125144578L,
                "coordinateZ", 1.2,
                "remark", "remark"));
        List<Map<String, Object>> firstFixedCameras = List.of(Map.of(
                "id", 201L,
                "mapId", 2077775285125144578L,
                "cameraName", "Fixed Camera"));
        when(centerClient.enabledMaps()).thenReturn(List.of(firstMap, secondMap, mapWithoutId));
        when(centerClient.mapPoints("2077775285125144578")).thenReturn(firstPoints);
        when(centerClient.fixedCameras()).thenReturn(firstFixedCameras);
        when(centerClient.fixedCameras("2077775285125144578")).thenReturn(firstFixedCameras);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(realtimeStatus(
                "robot-001", Map.of("mapId", 2077775285125144578L))));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "workflowDefinitionId", "definition-001",
                "roleBindings", List.of(Map.of("deviceIds", List.of("robot-001"))))));
        when(centerClient.taskWorkflowDefinition("definition-001")).thenReturn(Optional.of(Map.of(
                "mapId", 2077775285125144578L)));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> maps = maps(overview.get("map"));
        assertFalse(maps.get(0).containsKey("points"));
        assertEquals(12L, maps.get(0).get("fileId"));
        assertFalse(maps.get(0).containsKey("mapCode"));
        assertFalse(maps.get(0).containsKey("mapType"));
        assertFalse(maps.get(0).containsKey("regionId"));
        assertFalse(maps.get(0).containsKey("fileName"));
        assertFalse(maps.get(0).containsKey("previewImageUrl"));
        assertFalse(maps.get(0).containsKey("enabled"));
        assertFalse(maps.get(0).containsKey("remark"));
        Map<String, Object> resources = service.mapResources("2077775285125144578");
        assertEquals(List.of(Map.of("id", 101L, "pointName", "Start")), resources.get("points"));
        assertEquals(List.of("robot-001", "201"), resources.get("deviceIds"));
        assertEquals(firstFixedCameras, resources.get("fixedCamares"));
        assertFalse(firstMap.containsKey("points"));
        assertFalse(firstMap.containsKey("fixedCamares"));
        verify(centerClient, times(1)).mapPoints("2077775285125144578");
    }

    @Test
    void loadsTaskRoutesOnDemandInsteadOfPuttingThemInOverview() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        List<Map<String, Object>> mapPoints = List.of(Map.of("id", 101L, "pointName", "Start"));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区巡逻",
                "workflowDefinitionId", "definition-001")));
        when(centerClient.taskWorkflowDefinition("definition-001")).thenReturn(java.util.Optional.of(Map.of(
                "mapId", 1001L,
                "pathId", 2001L)));
        when(centerClient.mapPoints("1001")).thenReturn(mapPoints);
        when(centerClient.pathPoints("2001")).thenReturn(List.of(Map.of("mapPointId", 101L)));
        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();
        assertFalse(maps(overview.get("tasks")).get(0).containsKey("pathPoints"));
        Map<String, Object> routes = service.mapTaskRoutes("1001");
        assertEquals(mapPoints, maps(routes.get("items")).get(0).get("pathPoints"));
        verify(centerClient, times(1)).mapPoints("1001");
    }

    @Test
    void loadsTaskFixedCamerasOnDemandWithoutLeakingStreamCredentials() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.taskWorkflowPlanFixedCameras("1001")).thenReturn(List.of(
                Map.of(
                        "cameraId", "camera-001",
                        "cameraName", "东侧通道摄像头",
                        "subStreamUrl", "rtsp://example/sub",
                        "mainStreamUrl", "rtsp://example/main",
                        "username", "operator",
                        "password", "secret"),
                Map.of(
                        "cameraId", "camera-001",
                        "cameraName", "重复摄像头")));

        Map<String, Object> response = new PanoramaService(centerClient, new ObjectMapper())
                .taskFixedCameras("1001");

        List<Map<String, Object>> items = maps(response.get("items"));
        assertEquals(1, items.size());
        assertEquals(Map.of(
                "cameraId", "camera-001",
                "name", "东侧通道摄像头",
                "sourceType", "FIXED_CAMERA",
                "sourceId", "camera-001",
                "defaultQuality", "sub"), items.get(0));
        assertFalse(items.get(0).containsKey("mainStreamUrl"));
        assertFalse(items.get(0).containsKey("subStreamUrl"));
        assertFalse(items.get(0).containsKey("username"));
        assertFalse(items.get(0).containsKey("password"));
    }

    @Test
    void includesWorkflowInstanceIdInOverviewTasks() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区巡逻",
                "activeWorkflowInstanceId", 9001L,
                "activeWorkflowInstanceStatus", "FAILED",
                "executionMode", "SCHEDULE",
                "expectedDurationSeconds", 3600,
                "executionStatus", "RUNNING")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> tasks = maps(overview.get("tasks"));
        assertEquals(1L, tasks.get(0).get("taskId"));
        assertEquals(9001L, tasks.get(0).get("workflowInstanceId"));
        assertEquals("SCHEDULE", tasks.get(0).get("executionMode"));
        assertEquals(3600, tasks.get(0).get("expectedDurationSeconds"));
        assertEquals("running", tasks.get(0).get("status"));
    }

    @Test
    void usesPlanExecutionStatusInsteadOfHistoricalInstanceStatus() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区巡逻",
                "lastWorkflowInstanceId", 9001L,
                "executionStatus", "WAITING")));
        when(centerClient.taskWorkflowInstance("9001")).thenReturn(Optional.of(Map.of("status", "FAILED")));
        when(centerClient.taskWorkflowReplay("9001")).thenReturn(Optional.empty());
        when(centerClient.deviceTaskInstances("9001")).thenReturn(List.of());

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        Map<String, Object> task = maps(overview.get("tasks")).get(0);
        assertEquals("waiting", task.get("status"));
        assertEquals("待执行", task.get("statusName"));
        assertEquals(1L, ((Map<?, ?>) overview.get("taskOverview")).get("pending"));
        assertFalse(((Map<?, ?>) overview.get("taskOverview")).containsKey("completedRate"));
    }

    @Test
    void usesDeviceOnlineStatusForTaskEquipmentInsteadOfDeviceTaskStatus() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of())));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区巡逻",
                "activeWorkflowInstanceId", 9001L,
                "activeWorkflowInstanceStatus", "RUNNING",
                "executionStatus", "RUNNING")));
        when(centerClient.taskWorkflowInstance("9001")).thenReturn(Optional.empty());
        when(centerClient.taskWorkflowReplay("9001")).thenReturn(Optional.empty());
        when(centerClient.deviceTaskInstances("9001")).thenReturn(List.of(
                Map.of(
                        "serialNumber", "robot-001",
                        "deviceName", "Robot One",
                        "status", "TIMEOUT"),
                Map.of(
                        "serialNumber", "robot-missing",
                        "deviceName", "Missing Robot",
                        "status", "COMPLETED")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> equipment = maps(maps(overview.get("tasks")).get(0).get("equipmentList"));
        assertEquals(List.of(), equipment);
    }

    @Test
    void keepsRequiredAlarmFieldsWhileCompactingOverviewAlarmLocation() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.alarms(any(), any(), any())).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", "alarm-001"),
                Map.entry("title", "发生火灾"),
                Map.entry("alarmType", "BUSINESS"),
                Map.entry("severity", "HIGH"),
                Map.entry("occurredAt", "2026-08-14 10:00:00"),
                Map.entry("serialNumber", "robot-001"),
                Map.entry("taskId", "task-001"),
                Map.entry("status", "UNHANDLED"),
                Map.entry("location", Map.ofEntries(
                        Map.entry("lng", 106.0),
                        Map.entry("lat", 30.0),
                        Map.entry("altitude", 5.0),
                        Map.entry("x", 1.0),
                        Map.entry("y", 2.0),
                        Map.entry("z", 3.0),
                        Map.entry("address", "A区"),
                        Map.entry("updatedAt", "2026-08-14 10:00:00"))))));
        when(centerClient.taskWorkflowInstance("task-001")).thenReturn(Optional.empty());

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        Map<String, Object> alarms = map(overview.get("alarms"));
        assertEquals(1, alarms.get("total"));
        assertFalse(map(alarms.get("summary")).containsKey("handleRate"));
        Map<String, Object> alarm = maps(map(alarms.get("high")).get("items")).get(0);
        assertEquals("task-001", alarm.get("taskId"));
        assertEquals("unhandled", alarm.get("status"));
        Map<String, Object> location = map(alarm.get("location"));
        assertEquals(1.0, location.get("x"));
        assertEquals(2.0, location.get("y"));
        assertEquals(3.0, location.get("z"));
        assertFalse(location.containsKey("altitude"));
        assertFalse(location.containsKey("updatedAt"));
    }

    @Test
    void mapsManagementAlarmContractAndUsesFirstImageAsVisibleSnapshot() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.alarms(any(), any(), any())).thenReturn(List.of(Map.ofEntries(
                Map.entry("id", 1001L),
                Map.entry("sourceType", "COMPONENT"),
                Map.entry("severity", "CRITICAL"),
                Map.entry("status", "ACKNOWLEDGED"),
                Map.entry("title", "云台异常"),
                Map.entry("imageFileIds", List.of("file-001", "file-002")))));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        Map<String, Object> alarm = maps(map(map(overview.get("alarms")).get("high")).get("items")).get(0);
        assertEquals("组件告警", alarm.get("categoryName"));
        assertEquals("unhandled", alarm.get("status"));
        assertEquals(
                "/api/bigscreen/control/files/file-001/content",
                map(alarm.get("snapshotUrl")).get("visible"));
        assertNull(map(alarm.get("snapshotUrl")).get("thermal"));
        assertNull(map(alarm.get("snapshotUrl")).get("front"));
    }

    @Test
    void mapsActionableWorkflowAlarmAndHandlingActions() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        when(centerClient.actionableWorkflowAlarms()).thenReturn(List.of(Map.ofEntries(
                Map.entry("alarmId", 1001L),
                Map.entry("sourceType", "TASK"),
                Map.entry("severity", "WARN"),
                Map.entry("title", "检测异常"),
                Map.entry("workflowName", "火灾告警处理"),
                Map.entry("imageFileIds", List.of("file-001")))));
        when(centerClient.handleAlarm("1001", "FALSE_ALARM", "确认误报")).thenReturn(true);
        when(centerClient.handleWorkflowAlarm("1001", "HANDLE_NOW", "现场已处置")).thenReturn(true);
        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());

        Map<String, Object> actionable = service.actionableWorkflowAlarms();
        Map<String, Object> item = maps(actionable.get("items")).get(0);
        assertEquals("任务告警", item.get("categoryName"));
        assertEquals("MEDIUM", item.get("level"));
        assertEquals("火灾告警处理", item.get("taskName"));

        Map<String, Object> ordinary = service.disposeAlarm("1001", Map.of(
                "disposalStatus", "FALSE_ALARM",
                "handleResult", "确认误报"));
        Map<String, Object> workflow = service.handleWorkflowAlarm("1001", Map.of(
                "disposalStatus", "IMMEDIATE_DISPOSAL",
                "handleResult", "现场已处置"));
        assertEquals(true, ordinary.get("success"));
        assertEquals(true, workflow.get("success"));
        verify(centerClient).handleAlarm("1001", "FALSE_ALARM", "确认误报");
        verify(centerClient).handleWorkflowAlarm("1001", "HANDLE_NOW", "现场已处置");
    }

    @Test
    void fillsDeviceTasksFromAlreadyLoadedActiveTaskWithoutExtraQueries() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of())));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区-夜间巡逻",
                "activeWorkflowInstanceId", 9001L,
                "activeWorkflowInstanceStatus", "RUNNING")));
        when(centerClient.taskWorkflowInstance("9001")).thenReturn(Optional.of(Map.of(
                "id", 9001L,
                "status", "RUNNING",
                "startedAt", "2026-08-14T20:00:00",
                "completedAt", "2026-08-14T22:00:00")));
        when(centerClient.taskWorkflowReplay("9001")).thenReturn(Optional.empty());
        when(centerClient.deviceTaskInstances("9001")).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        assertFalse(maps(overview.get("devices")).get(0).containsKey("task"));
        verify(centerClient, never()).deviceTaskInstances("9001");
        verify(centerClient, never()).taskWorkflowInstance("9001");
    }

    @Test
    void doesNotFillCompletedTaskIntoDeviceCurrentTask() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of())));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "已完成任务",
                "lastWorkflowInstanceId", 9001L,
                "executionStatus", "COMPLETED")));
        when(centerClient.taskWorkflowInstance("9001")).thenReturn(Optional.of(Map.of("status", "COMPLETED")));
        when(centerClient.taskWorkflowReplay("9001")).thenReturn(Optional.empty());
        when(centerClient.deviceTaskInstances("9001")).thenReturn(List.of(Map.of("serialNumber", "robot-001")));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        assertFalse(maps(overview.get("devices")).get(0).containsKey("task"));
    }

    @Test
    void omitsRealtimeDeviceTaskFromOverviewBecauseTasksAreTheSingleSummarySource() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "onlineStatus", "ONLINE",
                "status", Map.of("task", Map.of(
                        "taskInstanceId", 9001L,
                        "taskStatus", "RUNNING")))));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "planName", "A区-夜间巡逻",
                "activeWorkflowInstanceId", 9001L,
                "activeWorkflowInstanceStatus", "RUNNING")));
        when(centerClient.taskWorkflowInstance("9001")).thenReturn(Optional.of(Map.of(
                "id", 9001L,
                "startedAt", "2026-08-14T20:00:00",
                "completedAt", "2026-08-14T22:00:00")));
        when(centerClient.taskWorkflowReplay("9001")).thenReturn(Optional.empty());
        when(centerClient.deviceTaskInstances("9001")).thenReturn(List.of(Map.of("serialNumber", "robot-001")));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        assertFalse(maps(overview.get("devices")).get(0).containsKey("task"));
    }

    @Test
    void omitsRedundantGpsDevicesFromOverview() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.enabledMaps()).thenReturn(List.of());
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "gps-001", "deviceName", "GPS Device"),
                Map.of("serialNumber", "local-001", "deviceName", "Local Device"),
                Map.of("serialNumber", "partial-001", "deviceName", "Partial GPS Device"),
                Map.of("serialNumber", "invalid-001", "deviceName", "Invalid GPS Device")));
        when(centerClient.realtimeStatuses(List.of("gps-001", "local-001", "partial-001", "invalid-001"))).thenReturn(List.of(
                realtimeStatus("gps-001", Map.of("longitude", "106.03", "latitude", "30.74")),
                realtimeStatus("local-001", Map.of("coordinateX", 10.0, "coordinateY", 20.0)),
                realtimeStatus("partial-001", Map.of("longitude", 106.04)),
                realtimeStatus("invalid-001", Map.of("longitude", 206.04, "latitude", 30.74))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(4, devices.size());
        assertFalse(overview.containsKey("gpsDevices"));
    }

    @Test
    void groupsDevicesIntoMapsByTaskMapId() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.enabledMaps()).thenReturn(List.of(
                Map.of("id", 1001L, "mapName", "Map One"),
                Map.of("mapId", "1002", "mapName", "Map Two"),
                Map.of("mapName", "Map Without Id")));
        when(centerClient.mapPoints("1001")).thenReturn(List.of());
        when(centerClient.mapPoints("1002")).thenReturn(List.of());
        when(centerClient.fixedCameras("1001")).thenReturn(List.of());
        when(centerClient.fixedCameras("1002")).thenReturn(List.of());
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "robot-001", "deviceName", "Robot One"),
                Map.of("serialNumber", "robot-002", "deviceName", "Robot Two"),
                Map.of("serialNumber", "robot-003", "deviceName", "Robot Without Map")));
        when(centerClient.realtimeStatuses(List.of("robot-001", "robot-002", "robot-003"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of("mapId", "slam-map-one", "coordinateX", 1.0)),
                realtimeStatus("robot-002", Map.of("mapId", "slam-map-two", "coordinateX", 2.0)),
                realtimeStatus("robot-003", Map.of("mapId", "unknown-map", "coordinateX", 3.0))));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(
                Map.of(
                        "id", 1L,
                        "workflowDefinitionId", "definition-001",
                        "roleBindings", List.of(Map.of("deviceIds", List.of("robot-001")))),
                Map.of(
                        "id", 2L,
                        "workflowDefinitionId", "definition-002",
                        "roleBindings", List.of(Map.of("deviceIds", List.of("robot-002"))))));
        when(centerClient.taskWorkflowDefinition("definition-001")).thenReturn(Optional.of(Map.of("mapId", 1001L)));
        when(centerClient.taskWorkflowDefinition("definition-002")).thenReturn(Optional.of(Map.of("mapId", "1002")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        List<Map<String, Object>> maps = maps(overview.get("map"));
        assertFalse(maps.get(0).containsKey("deviceIds"));
        assertNull(((Map<?, ?>) devices.get(0).get("location")).get("mapId"));
        assertNull(((Map<?, ?>) devices.get(1).get("location")).get("mapId"));
        assertNull(((Map<?, ?>) devices.get(2).get("location")).get("mapId"));
    }

    @Test
    void returnsTaskMapIdInDeviceDetail() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of("mapId", "2077"))));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", 1L,
                "workflowDefinitionId", "definition-001",
                "roleBindings", List.of(Map.of("deviceIds", List.of("robot-001"))))));
        when(centerClient.taskWorkflowDefinition("definition-001")).thenReturn(Optional.of(Map.of(
                "mapId", "2077775285125144578")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> detail = service.deviceDetail("robot-001");

        assertEquals(
                "2077775285125144578",
                ((Map<?, ?>) detail.get("location")).get("mapId"));
    }

    @Test
    void excludesRuntimeRegisteredRobotsWhenManagementListDoesNotAuthorizeThem() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.registeredRobots()).thenReturn(List.of(Map.of(
                "robotId", "test111",
                "clientId", "robot-media-client-test111",
                "name", "test111",
                "type", "机器人",
                "vendor", "Vendor",
                "status", "online",
                "battery", 80,
                "lastHeartbeatAt", "2026-08-14 10:00:00",
                "location", Map.of(
                        "mapId", "1001",
                        "longitude", 106.0,
                        "latitude", 30.0,
                        "altitude", 2.0,
                        "coordinateZ", 3.0,
                        "updatedAt", "2026-08-14 10:00:00"),
                "cameras", List.of(Map.of(
                        "cameraId", "test111",
                        "deviceId", "test111",
                        "groupType", "body",
                        "name", "本体相机",
                        "quality", "sub")))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertTrue(devices.isEmpty());
    }

    @Test
    void doesNotDuplicateRuntimeRegisteredRobotsAlreadyReturnedByManagement() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(Map.of("serialNumber", "robot-001", "deviceName", "Robot One")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(realtimeStatus("robot-001", Map.of())));
        when(centerClient.registeredRobots()).thenReturn(List.of(Map.of(
                "robotId", "robot-001",
                "name", "Runtime Robot",
                "status", "online",
                "cameras", List.of(Map.of(
                        "cameraId", "robot-001-front",
                        "deviceId", "body-camera-front",
                        "groupType", "body",
                        "name", "本体前相机",
                        "quality", "main")))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("robot-001", devices.get(0).get("robotId"));
        assertEquals("Robot One", devices.get(0).get("name"));
        assertEquals(List.of(Map.of(
                "cameraId", "robot-001-front",
                "deviceId", "body-camera-front",
                "groupType", "body",
                "name", "本体前相机",
                "quality", "main")), devices.get(0).get("cameras"));
    }

    @Test
    void keepsEnabledFixedCameraUnknownWithoutFreshHealth() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.fixedCameras()).thenReturn(List.of(Map.of(
                "id", "camera-001",
                "cameraName", "固定摄像头一",
                "mapId", "1001",
                "locationDescription", "A区",
                "coordinateX", 1.0,
                "coordinateY", 2.0,
                "headingYaw", 90,
                "protocolType", "RTSP",
                "enabled", true,
                "mainStreamUrl", "rtsp://example/camera-001")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("camera-001", devices.get(0).get("robotId"));
        assertEquals("FIXED_CAMERA", devices.get(0).get("sourceType"));
        assertEquals("offline", devices.get(0).get("status"));
        assertEquals(true, devices.get(0).get("enabled"));
        assertEquals("READY", devices.get(0).get("configStatus"));
        assertEquals("UNKNOWN", ((Map<?, ?>) devices.get(0).get("gatewayHealth")).get("status"));
        assertEquals("1001", ((Map<?, ?>) devices.get(0).get("location")).get("mapId"));
        assertEquals("camera-001", devices.get(0).get("equipmentId"));
        assertEquals("camera-001", devices.get(0).get("cameraId"));
        assertEquals("A区", devices.get(0).get("locationDescription"));
        assertEquals(1.0, devices.get(0).get("coordinateX"));
        assertEquals(2.0, devices.get(0).get("coordinateY"));
        assertEquals(90, devices.get(0).get("headingYaw"));
        assertEquals("RTSP", devices.get(0).get("protocolType"));
        assertEquals("main", devices.get(0).get("defaultQuality"));
        assertEquals(true, devices.get(0).get("playable"));
        assertEquals(false, devices.get(0).get("showControlCenter"));
        assertEquals(false, devices.get(0).get("showController"));
        Map<?, ?> stats = (Map<?, ?>) overview.get("deviceStats");
        assertEquals(1L, stats.get("offline"));
        assertFalse(stats.containsKey("unknown"));
        assertFalse(stats.containsKey("disabled"));
    }

    @Test
    void mapsFixedCameraOnlineOnlyWithFreshGatewayAndStreamHealth() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.fixedCameras()).thenReturn(List.of(Map.of(
                "cameraId", "camera-001", "cameraName", "固定摄像头一", "protocolType", "RTSP",
                "enabled", true, "mainStreamUrl", "rtsp://example/camera-001")));
        when(centerClient.fixedCameraHealth()).thenReturn(Map.of("records", List.of(Map.of(
                "cameraId", "camera-001",
                "gatewayId", "gateway-001",
                "gatewayHealth", Map.of("status", "ONLINE", "observedAt", "2026-08-23T00:00:00Z"),
                "streamHealth", Map.of("status", "AVAILABLE", "observedAt", "2026-08-23T00:00:00Z")))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();
        Map<String, Object> camera = maps(overview.get("devices")).get(0);
        assertEquals("online", camera.get("status"));
        assertEquals("ONLINE", ((Map<?, ?>) camera.get("gatewayHealth")).get("status"));
        assertEquals("AVAILABLE", ((Map<?, ?>) camera.get("streamHealth")).get("status"));
        assertEquals(1L, ((Map<?, ?>) overview.get("deviceStats")).get("online"));
        Map<String, Object> status = service.fixedCameraStatuses().get(0);
        assertEquals(Map.of(
                "sourceId", "camera-001",
                "status", "online",
                "playable", true,
                "enabled", true,
                "configReady", true), status);
    }

    @Test
    void resolvesRobotTypeNameFromManagementDictionaryAndKeepsFixedCameraDefault() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.deviceTypeOptions()).thenReturn(List.of(Map.of(
                "label", "轮式巡检车",
                "value", "WHEELED_ROBOT")));
        when(centerClient.devices()).thenReturn(List.of(Map.of(
                "serialNumber", "robot-001",
                "deviceName", "Robot One",
                "deviceType", "WHEELED_ROBOT")));
        when(centerClient.realtimeStatuses(List.of("robot-001"))).thenReturn(List.of(
                realtimeStatus("robot-001", Map.of())));
        when(centerClient.fixedCameras()).thenReturn(List.of(Map.of(
                "id", "camera-001",
                "cameraName", "固定摄像头一",
                "enabled", true,
                "mainStreamUrl", "rtsp://example/camera-001")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();
        List<Map<String, Object>> devices = maps(overview.get("devices"));

        assertEquals("WHEELED_ROBOT", devices.get(0).get("typeCode"));
        assertEquals("轮式巡检车", devices.get(0).get("type"));
        assertEquals("FIXED_CAMERA", devices.get(1).get("typeCode"));
        assertEquals("固定摄像头", devices.get(1).get("type"));
        List<Map<String, Object>> typeStats = maps(overview.get("deviceTypeStats"));
        assertEquals("WHEELED_ROBOT", typeStats.get(0).get("type"));
        assertEquals("轮式巡检车", typeStats.get(0).get("name"));
        assertEquals(1, typeStats.get(0).get("count"));
        assertEquals("FIXED_CAMERA", typeStats.get(1).get("type"));
        assertEquals("固定摄像头", typeStats.get(1).get("name"));
        assertEquals(1, typeStats.get(1).get("count"));
        verify(centerClient).deviceTypeOptions();
    }

    @Test
    void countsOnlyExplicitHealthFailuresAsFaults() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "normal-001"),
                Map.of("serialNumber", "unknown-001"),
                Map.of("serialNumber", "fault-001")));
        when(centerClient.realtimeStatuses(List.of("normal-001", "unknown-001", "fault-001"))).thenReturn(List.of(
                realtimeStatusWithHealth("normal-001", "NORMAL"),
                realtimeStatusWithHealth("unknown-001", "UNKNOWN"),
                realtimeStatusWithHealth("fault-001", "FAULT")));
        when(centerClient.registeredRobots()).thenReturn(List.of(
                Map.of("robotId", "normal-001", "status", "online"),
                Map.of("robotId", "unknown-001", "status", "online"),
                Map.of("robotId", "fault-001", "status", "fault")));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();
        List<Map<String, Object>> devices = maps(overview.get("devices"));

        assertEquals(false, devices.get(0).get("fault"));
        assertEquals(false, devices.get(1).get("fault"));
        assertEquals(true, devices.get(2).get("fault"));
        assertEquals("fault", devices.get(2).get("status"));
        assertEquals(1L, ((Map<?, ?>) overview.get("deviceStats")).get("fault"));
    }

    @Test
    void usesLocalControlRegistryAsAuthoritativeOnlineStatusSource() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.devices()).thenReturn(List.of(
                Map.of("serialNumber", "robot-offline"),
                Map.of("serialNumber", "robot-online"),
                Map.of("serialNumber", "robot-missing")));
        when(centerClient.realtimeStatuses(List.of("robot-offline", "robot-online", "robot-missing")))
                .thenReturn(List.of(
                        realtimeStatus("robot-offline", Map.of()),
                        Map.of("serialNumber", "robot-online", "onlineStatus", "OFFLINE", "status", Map.of()),
                        realtimeStatus("robot-missing", Map.of())));
        when(centerClient.registeredRobots()).thenReturn(List.of(
                Map.of(
                        "robotId", "robot-offline",
                        "status", "offline",
                        "statusChangedAt", "2026-08-28 10:00:00"),
                Map.of(
                        "robotId", "robot-online",
                        "status", "online",
                        "statusChangedAt", "2026-08-28 10:00:01")));

        List<Map<String, Object>> devices = maps(
                new PanoramaService(centerClient, new ObjectMapper()).overview().get("devices"));

        assertEquals("offline", devices.get(0).get("status"));
        assertEquals("2026-08-28 10:00:00", devices.get(0).get("statusChangedAt"));
        assertEquals("online", devices.get(1).get("status"));
        assertEquals("2026-08-28 10:00:01", devices.get(1).get("statusChangedAt"));
        assertEquals("offline", devices.get(2).get("status"));
        assertTrue(devices.get(2).get("statusChangedAt") instanceof String);
    }

    private Map<String, Object> realtimeStatus(String serialNumber, Map<String, Object> localization) {
        return Map.of(
                "serialNumber", serialNumber,
                "onlineStatus", "ONLINE",
                "status", Map.of("localization", localization));
    }

    private Map<String, Object> realtimeStatusWithHealth(String serialNumber, String healthStatus) {
        return Map.of(
                "serialNumber", serialNumber,
                "onlineStatus", "ONLINE",
                "status", Map.of("basic", Map.of("healthStatus", healthStatus)));
    }

    @Test
    void statsSnapshotOnlyRecalculatesRequestedParts() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());

        Map<String, Object> snapshot = service.statsSnapshot(Set.of(StatsPart.ALARMS));

        assertTrue(snapshot.containsKey("alarmStats"));
        assertTrue(snapshot.containsKey("alarmSummary"));
        assertFalse(snapshot.containsKey("deviceStats"));
        assertFalse(snapshot.containsKey("taskOverview"));
        assertFalse(snapshot.containsKey("patrolOverview"));
        verify(centerClient, times(2)).alarms(any(), any(), any());
        verify(centerClient, never()).devices();
        verify(centerClient, never()).taskWorkflowPlans();
        verify(centerClient, never()).mileageSummary(any(), any(), any());
    }

    @Test
    void statsSnapshotCachesEachPartWithinTtl() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());

        service.statsSnapshot(Set.of(StatsPart.ALARMS));
        service.statsSnapshot(Set.of(StatsPart.ALARMS));
        service.statsSnapshot(Set.of(StatsPart.ALARMS));

        verify(centerClient, times(2)).alarms(any(), any(), any());
    }

    @Test
    void marksTaskDataDegradedWhenManagementTaskQueryTimesOut() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.taskWorkflowPlans()).thenThrow(new PanoramaCenterClient.TaskSourceException(
                "TASK_QUERY_TIMEOUT", "Management 任务接口读取超时"));

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        Map<String, Object> taskQuality = map(map(overview.get("dataQuality")).get("tasks"));
        assertEquals(false, taskQuality.get("complete"));
        assertEquals(true, taskQuality.get("degraded"));
        assertTrue(((List<?>) taskQuality.get("reasonCodes")).contains("TASK_QUERY_TIMEOUT"));
        assertEquals(List.of(), maps(overview.get("tasks")));
    }

    @Test
    void isolatesMissingWorkflowInstanceAndReportsInvalidReference() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of(Map.of(
                "id", "plan-001",
                "planName", "失效引用任务",
                "workflowInstanceId", "instance-deleted")));
        when(centerClient.taskWorkflowInstance("instance-deleted")).thenReturn(Optional.empty());

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();

        Map<String, Object> taskQuality = map(map(overview.get("dataQuality")).get("tasks"));
        assertEquals(true, taskQuality.get("complete"));
        assertEquals(List.of(), taskQuality.get("reasonCodes"));
        assertEquals(1, maps(overview.get("tasks")).size());
    }

    private void stubEmptyOverviewSources(PanoramaCenterClient centerClient) {
        when(centerClient.devices()).thenReturn(List.of());
        when(centerClient.deviceTypeOptions()).thenReturn(List.of());
        when(centerClient.registeredRobots()).thenReturn(List.of());
        when(centerClient.fixedCameras()).thenReturn(List.of());
        when(centerClient.fixedCameraHealth()).thenReturn(Map.of("records", List.of()));
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of());
        when(centerClient.taskWorkflowInstances()).thenReturn(List.of());
        when(centerClient.alarms(any(), any(), any())).thenReturn(List.of());
    }

    private void authenticate(String subject, String orgId) {
        Jwt jwt = Jwt.withTokenValue("token-" + subject + "-" + orgId)
                .header("alg", "none")
                .issuer("https://iam.example/realms/platform")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("org_id", orgId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_MEDIA_VIEWER"))));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
