package com.robot.bigscreen.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaService;
import com.robot.bigscreen.panorama.StatsPart;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;

class PanoramaStatsEventRefresherTest {

    @Test
    void debouncesAndDoesNotPublishAnUnchangedSnapshot() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> snapshot = Map.of(
                "deviceStats", Map.of("total", 2, "online", 1, "fault", 0, "offline", 1),
                "deviceTypeStats", List.of(Map.of("type", "WHEELED_ROBOT", "name", "轮式机器人", "count", 2)),
                "taskOverview", Map.of("totalToday", 1),
                "patrolOverview", Map.of("durationToday", 1.5),
                "alarmStats", Map.of("high", 0, "medium", 0, "low", 0),
                "alarmSummary", Map.of("totalToday", 0));
        when(panoramaService.statsSnapshot(any())).thenReturn(snapshot);
        when(panoramaService.fixedCameraStatuses()).thenReturn(List.of(Map.of(
                "sourceId", "camera-001",
                "status", "online",
                "playable", true,
                "enabled", true,
                "configReady", true)));
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler, new SyncTaskExecutor());
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.ALARMS));
        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.TASKS));
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        tasks.getValue().run();

        JsonNode event = objectMapper.readTree(events.get(0));
        assertThat(event.path("event").asText()).isEqualTo("panorama.stats.changed");
        assertThat(event.path("data").path("deviceStats").path("total").asInt()).isEqualTo(2);
        assertThat(event.path("data").path("deviceTypeStats").get(0).path("name").asText()).isEqualTo("轮式机器人");

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.ALARMS));
        tasks.getAllValues().get(1).run();
        assertThat(events).hasSize(1);
    }

    @Test
    void mergesRequestedPartsBeforeRefreshing() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> snapshot = Map.of("alarmStats", Map.of("high", 1));
        when(panoramaService.statsSnapshot(any())).thenReturn(snapshot);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler, new SyncTaskExecutor());
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.ALARMS));
        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.TASKS, StatsPart.DEVICES));
        tasks.getValue().run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<StatsPart>> parts = ArgumentCaptor.forClass(Set.class);
        verify(panoramaService).statsSnapshot(parts.capture());
        assertThat(parts.getValue()).containsExactlyInAnyOrder(StatsPart.ALARMS, StatsPart.TASKS, StatsPart.DEVICES);
        assertThat(events).hasSize(1);
    }

    @Test
    void publishesAuthorizedFixedCameraStatusesOnlyForDeviceRefresh() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(panoramaService.statsSnapshot(any())).thenReturn(Map.of(
                "deviceStats", Map.of("total", 1, "online", 1, "fault", 0, "offline", 0)));
        when(panoramaService.fixedCameraStatuses()).thenReturn(List.of(Map.of(
                "sourceId", "camera-001",
                "status", "online",
                "playable", true,
                "enabled", true,
                "configReady", true)));
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler, new SyncTaskExecutor());
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.DEVICES));
        tasks.getValue().run();

        assertThat(events).hasSize(2);
        JsonNode statusEvent = objectMapper.readTree(events.get(1));
        assertThat(statusEvent.path("event").asText()).isEqualTo("panorama.fixed-camera.statuses.changed");
        assertThat(statusEvent.path("data").path("items").get(0).path("sourceId").asText())
                .isEqualTo("camera-001");
        assertThat(statusEvent.toString()).doesNotContain("gatewayId", "streamHealth");
    }

    @Test
    void publishesAuthorizedEmptyDeviceStatsWithoutKeepingStaleSnapshot() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> first = Map.of(
                "deviceStats", Map.of("total", 1, "online", 1, "fault", 1, "offline", 0),
                "deviceTypeStats", List.of(Map.of("type", "ROBOT_DOG", "name", "机器狗", "count", 1)),
                "taskOverview", Map.of("totalToday", 0));
        Map<String, Object> transientEmpty = Map.of(
                "deviceStats", Map.of("total", 0, "online", 0, "fault", 0, "offline", 0),
                "deviceTypeStats", List.of(),
                "taskOverview", Map.of("totalToday", 1));
        when(panoramaService.statsSnapshot(any())).thenReturn(first, transientEmpty);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler, new SyncTaskExecutor());
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.DEVICES));
        tasks.getValue().run();
        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.DEVICES, StatsPart.TASKS));
        tasks.getAllValues().get(1).run();

        assertThat(events).hasSize(2);
        JsonNode second = objectMapper.readTree(events.get(1));
        assertThat(second.path("data").path("deviceStats").path("total").asInt()).isZero();
        assertThat(second.path("data").path("deviceTypeStats")).isEmpty();
        assertThat(second.path("data").path("taskOverview").path("totalToday").asInt()).isEqualTo(1);
    }

    @Test
    void keepsSuccessfulBlocksAndDeepMergesQualityWhenAnotherPartDegrades() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(panoramaService.statsSnapshot(any())).thenReturn(
                Map.of(
                        "taskOverview", Map.of("totalToday", 12),
                        "dataQuality", Map.of("tasks", Map.of("complete", true, "degraded", false))),
                Map.of("dataQuality", Map.of("alarms", Map.of(
                        "complete", false,
                        "degraded", true,
                        "reasonCodes", List.of("ALARM_QUERY_TIMEOUT")))));
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler, new SyncTaskExecutor());
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.TASKS));
        tasks.getValue().run();
        refresher.requestRefresh("browser-a", null, events::add, Set.of(StatsPart.ALARMS));
        tasks.getAllValues().get(1).run();

        JsonNode second = objectMapper.readTree(events.get(1)).path("data");
        assertThat(second.path("taskOverview").path("totalToday").asInt()).isEqualTo(12);
        assertThat(second.path("dataQuality").path("tasks").path("complete").asBoolean()).isTrue();
        assertThat(second.path("dataQuality").path("alarms").path("degraded").asBoolean()).isTrue();
    }

    @Test
    void doesNotPublishAQueryThatCompletesAfterIdentityRemoval() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        AtomicReference<PanoramaStatsEventRefresher> reference = new AtomicReference<>();
        when(panoramaService.statsSnapshot(any())).thenAnswer(ignored -> {
            reference.get().remove("identity-a");
            return Map.of("taskOverview", Map.of("totalToday", 9));
        });
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, new ObjectMapper(), taskScheduler, new SyncTaskExecutor());
        reference.set(refresher);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("identity-a", null, events::add, Set.of(StatsPart.TASKS));
        tasks.getValue().run();

        assertThat(events).isEmpty();
    }

    @Test
    void schedulerOnlyDispatchesRefreshWork() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        when(panoramaService.statsSnapshot(any())).thenReturn(Map.of());
        List<Runnable> executions = new ArrayList<>();
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, new ObjectMapper(), taskScheduler, executions::add);
        refresher.requestRefresh("browser-a", null, ignored -> { }, Set.of(StatsPart.TASKS));

        tasks.getValue().run();
        verify(panoramaService).invalidateTaskStats();
        verify(panoramaService, times(0)).statsSnapshot(any());
        assertThat(executions).hasSize(1);

        executions.get(0).run();
        verify(panoramaService).statsSnapshot(any());
    }
}
