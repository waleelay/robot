package com.robot.bigscreen.panorama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PanoramaServiceTest {

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
    void addsPointsAndFixedCamerasToEveryMapInOverview() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        Map<String, Object> firstMap = Map.of("id", 2077775285125144578L, "mapName", "Map One");
        Map<String, Object> secondMap = Map.of("mapId", 2, "mapName", "Map Two");
        Map<String, Object> mapWithoutId = Map.of("mapName", "Map Without Id");
        List<Map<String, Object>> firstPoints = List.of(Map.of("id", 101L, "pointName", "Start"));
        List<Map<String, Object>> firstFixedCameras = List.of(Map.of(
                "id", 201L,
                "mapId", 2077775285125144578L,
                "cameraName", "Fixed Camera"));
        when(centerClient.enabledMaps()).thenReturn(List.of(firstMap, secondMap, mapWithoutId));
        when(centerClient.mapPoints("2077775285125144578")).thenReturn(firstPoints);
        when(centerClient.mapPoints("2")).thenReturn(List.of());
        when(centerClient.fixedCameras()).thenReturn(firstFixedCameras);

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> maps = maps(overview.get("map"));
        assertEquals(firstPoints, maps.get(0).get("points"));
        assertEquals(List.of(), maps.get(1).get("points"));
        assertEquals(List.of(), maps.get(2).get("points"));
        assertEquals(1, maps(maps.get(0).get("devices")).size());
        assertEquals("201", maps(maps.get(0).get("devices")).get(0).get("robotId"));
        assertEquals(List.of(), maps.get(1).get("devices"));
        assertEquals(List.of(), maps.get(2).get("devices"));
        assertEquals(firstFixedCameras, maps.get(0).get("fixedCamares"));
        assertEquals(List.of(), maps.get(1).get("fixedCamares"));
        assertEquals(List.of(), maps.get(2).get("fixedCamares"));
        assertFalse(firstMap.containsKey("points"));
        assertFalse(firstMap.containsKey("fixedCamares"));
        verify(centerClient).mapPoints("2077775285125144578");
        verify(centerClient).mapPoints("2");
        verify(centerClient, times(1)).fixedCameras();
    }

    @Test
    void reusesMapPointsBetweenTaskRoutesAndMapPayloadInOverview() {
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
        when(centerClient.enabledMaps()).thenReturn(List.of(Map.of("id", 1001L, "mapName", "Map One")));
        when(centerClient.fixedCameras("1001")).thenReturn(List.of());

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        assertEquals(mapPoints, maps(overview.get("map")).get(0).get("points"));
        assertEquals(mapPoints, maps(maps(overview.get("tasks")).get(0).get("mapPoints")));
        verify(centerClient, times(1)).mapPoints("1001");
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
        assertEquals("online", equipment.get(0).get("status"));
        assertNull(equipment.get(1).get("status"));
    }

    @Test
    void includesOnlyDevicesWithCompleteGpsCoordinates() {
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
        List<Map<String, Object>> gpsDevices = maps(overview.get("gpsDevices"));
        assertEquals(4, devices.size());
        assertEquals(List.of(devices.get(0)), gpsDevices);
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
        assertEquals(List.of(devices.get(0)), maps(maps.get(0).get("devices")));
        assertEquals(List.of(devices.get(1)), maps(maps.get(1).get("devices")));
        assertEquals(List.of(), maps.get(2).get("devices"));
        assertEquals("1001", ((Map<?, ?>) devices.get(0).get("location")).get("mapId"));
        assertEquals("1002", ((Map<?, ?>) devices.get(1).get("location")).get("mapId"));
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
    void includesRuntimeRegisteredRobotsWhenManagementListDoesNotContainThem() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.registeredRobots()).thenReturn(List.of(Map.of(
                "robotId", "test111",
                "clientId", "robot-media-client-test111",
                "name", "test111",
                "type", "机器人",
                "status", "online",
                "battery", 80,
                "cameras", List.of(Map.of(
                        "cameraId", "test111",
                        "deviceId", "test111",
                        "groupType", "body",
                        "name", "本体相机",
                        "quality", "sub")))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("test111", devices.get(0).get("robotId"));
        assertEquals("online", devices.get(0).get("status"));
        assertEquals("robot-media-client-test111", devices.get(0).get("clientId"));
        assertEquals(1, maps(devices.get(0).get("cameras")).size());
        assertNull(devices.get(0).get("typeCode"));
        assertNull(devices.get(0).get("type"));
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
                "status", "online")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("robot-001", devices.get(0).get("robotId"));
        assertEquals("Robot One", devices.get(0).get("name"));
    }

    @Test
    void mapsEnabledFixedCamerasAsOnlineDevices() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        when(centerClient.fixedCameras()).thenReturn(List.of(Map.of(
                "id", "camera-001",
                "cameraName", "固定摄像头一",
                "mapId", "1001",
                "enabled", true,
                "mainStreamUrl", "rtsp://example/camera-001")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("camera-001", devices.get(0).get("robotId"));
        assertEquals("FIXED_CAMERA", devices.get(0).get("sourceType"));
        assertEquals("online", devices.get(0).get("status"));
        assertEquals("1001", ((Map<?, ?>) devices.get(0).get("location")).get("mapId"));
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

        Map<String, Object> overview = new PanoramaService(centerClient, new ObjectMapper()).overview();
        List<Map<String, Object>> devices = maps(overview.get("devices"));

        assertEquals(false, devices.get(0).get("fault"));
        assertNull(devices.get(1).get("fault"));
        assertEquals(true, devices.get(2).get("fault"));
        assertEquals(1L, ((Map<?, ?>) overview.get("deviceStats")).get("fault"));
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

    private void stubEmptyOverviewSources(PanoramaCenterClient centerClient) {
        when(centerClient.devices()).thenReturn(List.of());
        when(centerClient.deviceTypeOptions()).thenReturn(List.of());
        when(centerClient.registeredRobots()).thenReturn(List.of());
        when(centerClient.fixedCameras()).thenReturn(List.of());
        when(centerClient.taskWorkflowPlans()).thenReturn(List.of());
        when(centerClient.taskWorkflowInstances()).thenReturn(List.of());
        when(centerClient.alarms()).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
