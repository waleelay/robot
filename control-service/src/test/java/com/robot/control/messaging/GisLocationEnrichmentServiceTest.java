package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlManagementClient.GisConversion;
import com.robot.control.client.ControlManagementClient.GisCoordinate;
import com.robot.control.robot.service.RobotRegistryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GisLocationEnrichmentServiceTest {

    @Test
    void batchesOnlyTheLatestMissingGisLocationPerDevice() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenReturn(List.of(
                new GisConversion("robot-1", "map-1", 2.0, 3.0, 104.1, 30.2, true, null)));

        service.observe("robot-1", location("map-1", 1.0, 3.0));
        service.observe("robot-1", location("map-1", 2.0, 3.0));
        service.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GisCoordinate>> request = ArgumentCaptor.forClass(List.class);
        verify(client).convertGis(request.capture());
        assertThat(request.getValue()).containsExactly(new GisCoordinate("robot-1", "map-1", 2.0, 3.0));
        verify(registry).enrichLocationIfConnected("robot-1", "map-1", 2.0, 3.0, 104.1, 30.2);
    }

    @Test
    void doesNotRequestConversionWhenEdgeAlreadyProvidesValidGisCoordinates() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        Map<String, Object> location = location("map-1", 1.0, 2.0);
        location.put("longitude", 104.1);
        location.put("latitude", 30.2);

        service.observe("robot-1", location);
        service.flush();

        verify(client, never()).convertGis(anyList());
    }

    @Test
    void reusesRecentSuccessfulConversionForTheSameLocation() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenReturn(List.of(
                new GisConversion("robot-1", "map-1", 1.0, 2.0, 104.1, 30.2, true, null)));

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();
        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();

        verify(client).convertGis(anyList());
        verify(registry, times(2))
                .enrichLocationIfConnected("robot-1", "map-1", 1.0, 2.0, 104.1, 30.2);
    }

    @Test
    void coolsDownMapFailureEvenWhenTheDeviceMoves() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenReturn(List.of(
                new GisConversion("robot-1", "map-1", 1.0, 2.0, null, null, false, "MAP_NOT_FOUND")));

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();
        service.observe("robot-1", location("map-1", 9.0, 8.0));
        service.flush();

        verify(client).convertGis(anyList());
        verify(registry, never()).enrichLocationIfConnected(
                "robot-1", "map-1", 1.0, 2.0, null, null);
    }

    @Test
    void handlesInvalidConvertedResponseWithoutAReason() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenReturn(List.of(
                new GisConversion("robot-1", "map-1", 1.0, 2.0, null, null, true, null)));

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();

        verify(registry, never()).enrichLocationIfConnected(
                "robot-1", "map-1", 1.0, 2.0, null, null);
    }

    @Test
    void limitsEachManagementRequestToOneHundredDevices() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(client.convertGis(anyList())).thenReturn(List.of());
        for (int index = 0; index < 101; index++) {
            when(registry.isConnected("robot-" + index)).thenReturn(true);
            service.observe("robot-" + index, location("map-1", index, index));
        }

        service.flush();
        service.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GisCoordinate>> requests = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).convertGis(requests.capture());
        assertThat(requests.getAllValues()).extracting(List::size).containsExactlyInAnyOrder(100, 1);
    }

    @Test
    void discardsPendingLocationWhenDeviceIsOffline() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();

        verify(client, never()).convertGis(anyList());
    }

    @Test
    void waitsForTheNextStatusReportAfterTransportFailure() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenThrow(new IllegalStateException("unavailable"));

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();
        service.flush();
        verify(client).convertGis(anyList());

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();
        verify(client, times(2)).convertGis(anyList());
    }

    @Test
    void rejectsResponseForDifferentCoordinates() {
        ControlManagementClient client = mock(ControlManagementClient.class);
        RobotRegistryService registry = mock(RobotRegistryService.class);
        GisLocationEnrichmentService service = new GisLocationEnrichmentService(client, registry);
        when(registry.isConnected("robot-1")).thenReturn(true);
        when(client.convertGis(anyList())).thenReturn(List.of(
                new GisConversion("robot-1", "map-1", 9.0, 2.0, 104.1, 30.2, true, null)));

        service.observe("robot-1", location("map-1", 1.0, 2.0));
        service.flush();

        verify(registry, never()).enrichLocationIfConnected("robot-1", "map-1", 1.0, 2.0, 104.1, 30.2);
    }

    private Map<String, Object> location(String mapId, double x, double y) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("mapId", mapId);
        location.put("x", x);
        location.put("y", y);
        return location;
    }
}
