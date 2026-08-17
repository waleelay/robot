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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(tasks.capture(), any(Instant.class))).thenReturn(null);
        PanoramaStatsEventRefresher refresher = new PanoramaStatsEventRefresher(
                panoramaService, objectMapper, taskScheduler);
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
                panoramaService, objectMapper, taskScheduler);
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
}
