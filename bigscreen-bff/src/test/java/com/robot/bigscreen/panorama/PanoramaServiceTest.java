package com.robot.bigscreen.panorama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PanoramaServiceTest {

    @Test
    void addsPointsAndFixedCamerasToEveryMapInOverview() {
        PanoramaCenterClient centerClient = mock(PanoramaCenterClient.class);
        stubEmptyOverviewSources(centerClient);
        Map<String, Object> firstMap = Map.of("id", 2077775285125144578L, "mapName", "Map One");
        Map<String, Object> secondMap = Map.of("mapId", 2, "mapName", "Map Two");
        Map<String, Object> mapWithoutId = Map.of("mapName", "Map Without Id");
        List<Map<String, Object>> firstPoints = List.of(Map.of("id", 101L, "pointName", "Start"));
        List<Map<String, Object>> firstFixedCameras = List.of(Map.of("id", 201L, "cameraName", "Fixed Camera"));
        when(centerClient.enabledMaps()).thenReturn(List.of(firstMap, secondMap, mapWithoutId));
        when(centerClient.mapPoints("2077775285125144578")).thenReturn(firstPoints);
        when(centerClient.mapPoints("2")).thenReturn(List.of());
        when(centerClient.fixedCameras("2077775285125144578")).thenReturn(firstFixedCameras);
        when(centerClient.fixedCameras("2")).thenReturn(List.of());

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> maps = maps(overview.get("map"));
        assertEquals(firstPoints, maps.get(0).get("points"));
        assertEquals(List.of(), maps.get(1).get("points"));
        assertEquals(List.of(), maps.get(2).get("points"));
        assertEquals(List.of(), maps.get(0).get("devices"));
        assertEquals(List.of(), maps.get(1).get("devices"));
        assertEquals(List.of(), maps.get(2).get("devices"));
        assertEquals(firstFixedCameras, maps.get(0).get("fixedCamares"));
        assertEquals(List.of(), maps.get(1).get("fixedCamares"));
        assertEquals(List.of(), maps.get(2).get("fixedCamares"));
        assertFalse(firstMap.containsKey("points"));
        assertFalse(firstMap.containsKey("fixedCamares"));
        verify(centerClient).mapPoints("2077775285125144578");
        verify(centerClient).mapPoints("2");
        verify(centerClient).fixedCameras("2077775285125144578");
        verify(centerClient).fixedCameras("2");
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
    void groupsDevicesIntoMapsByRealtimeLocalizationMapId() {
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
                realtimeStatus("robot-001", Map.of("mapId", 1001L, "coordinateX", 1.0)),
                realtimeStatus("robot-002", Map.of("mapId", "1002", "coordinateX", 2.0)),
                realtimeStatus("robot-003", Map.of("coordinateX", 3.0))));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        List<Map<String, Object>> maps = maps(overview.get("map"));
        assertEquals(List.of(devices.get(0)), maps(maps.get(0).get("devices")));
        assertEquals(List.of(devices.get(1)), maps(maps.get(1).get("devices")));
        assertEquals(List.of(), maps.get(2).get("devices"));
        assertEquals(1001L, ((Map<?, ?>) devices.get(0).get("location")).get("mapId"));
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
                "enabled", true,
                "mainStreamUrl", "rtsp://example/camera-001")));

        PanoramaService service = new PanoramaService(centerClient, new ObjectMapper());
        Map<String, Object> overview = service.overview();

        List<Map<String, Object>> devices = maps(overview.get("devices"));
        assertEquals(1, devices.size());
        assertEquals("camera-001", devices.get(0).get("robotId"));
        assertEquals("FIXED_CAMERA", devices.get(0).get("sourceType"));
        assertEquals("online", devices.get(0).get("status"));
    }

    private Map<String, Object> realtimeStatus(String serialNumber, Map<String, Object> localization) {
        return Map.of(
                "serialNumber", serialNumber,
                "onlineStatus", "ONLINE",
                "status", Map.of("localization", localization));
    }

    private void stubEmptyOverviewSources(PanoramaCenterClient centerClient) {
        when(centerClient.devices()).thenReturn(List.of());
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
