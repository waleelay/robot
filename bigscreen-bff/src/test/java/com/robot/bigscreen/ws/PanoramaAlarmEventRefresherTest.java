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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

class PanoramaAlarmEventRefresherTest {

    @Test
    void coalescesInvalidationsAndPublishesOnlyChangedAlarmSnapshots() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> unhandled = Map.of("alarmId", "alarm-1", "level", "HIGH", "status", "unhandled");
        Map<String, Object> handled = Map.of("alarmId", "alarm-1", "level", "HIGH", "status", "handled");
        when(panoramaService.alarmEventSnapshot())
                .thenReturn(snapshot(unhandled, 1))
                .thenReturn(snapshot(unhandled, 1))
                .thenReturn(snapshot(handled, 1))
                .thenReturn(snapshot(null, 0));
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of(
                "items", List.of(Map.of("alarmId", "workflow-1"))));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, objectMapper, scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add);
        refresher.requestRefresh("browser-a", null, events::add);
        verify(taskExecutor, times(2)).execute(any(Runnable.class));
        jobs.getAllValues().get(0).run();
        jobs.getAllValues().get(1).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(3).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(4).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(5).run();

        List<String> alarmEvents = events.stream()
                .filter(value -> value.contains("\"panorama.alarms.changed\""))
                .toList();
        assertThat(alarmEvents).hasSize(3);
        JsonNode event = objectMapper.readTree(alarmEvents.get(1));
        assertThat(event.path("event").asText()).isEqualTo("panorama.alarms.changed");
        assertThat(event.path("data").path("high").path("items").get(0).path("status").asText())
                .isEqualTo("handled");
        JsonNode removed = objectMapper.readTree(alarmEvents.get(2));
        assertThat(removed.path("data").path("high").path("items")).isEmpty();
    }

    @Test
    void retriesWorkflowIndependentlyUntilSnapshotChanges() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> actionable = Map.of(
                "alarmId", "alarm-1",
                "sourceType", "TASK",
                "level", "MEDIUM",
                "status", "unhandled",
                "workflowActionable", true);
        when(panoramaService.actionableWorkflowAlarms())
                .thenReturn(Map.of("items", List.of()))
                .thenReturn(Map.of("items", List.of()))
                .thenReturn(Map.of("items", List.of(actionable)));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> scheduledJobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        when(scheduler.schedule(scheduledJobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, objectMapper, scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestSnapshot("browser-a", null, events::add);
        jobs.getAllValues().get(0).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(1).run();
        scheduledJobs.getValue().run();
        jobs.getAllValues().get(3).run();

        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verify(panoramaService, times(3)).actionableWorkflowAlarms();
        verify(panoramaService, times(0)).alarmEventSnapshot();
        List<String> workflowEvents = events.stream()
                .filter(value -> value.contains("\"panorama.workflow-alarms.changed\""))
                .toList();
        assertThat(workflowEvents).hasSize(2);
        JsonNode first = objectMapper.readTree(workflowEvents.get(0));
        JsonNode second = objectMapper.readTree(workflowEvents.get(1));
        assertThat(first.path("data").path("items")).isEmpty();
        assertThat(second.path("data").path("items").get(0).path("alarmId").asText()).isEqualTo("alarm-1");
    }

    @Test
    void connectionSnapshotStillConvergesWhenAlarmInvalidationArrivesBeforeFirstQuery() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> actionable = Map.of("alarmId", "alarm-1", "workflowActionable", true);
        when(panoramaService.actionableWorkflowAlarms())
                .thenReturn(Map.of("items", List.of()))
                .thenReturn(Map.of("items", List.of(actionable)));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> scheduledJobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        when(scheduler.schedule(scheduledJobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, objectMapper, scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestSnapshot("browser-a", null, events::add);
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(0).run();
        scheduledJobs.getValue().run();
        jobs.getAllValues().get(2).run();

        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
        List<String> workflowEvents = events.stream()
                .filter(value -> value.contains("\"panorama.workflow-alarms.changed\""))
                .toList();
        assertThat(workflowEvents).hasSize(2);
        assertThat(objectMapper.readTree(workflowEvents.get(1))
                .path("data").path("items").get(0).path("alarmId").asText()).isEqualTo("alarm-1");
    }

    private Map<String, Object> snapshot(Map<String, Object> alarm, int total) {
        List<Map<String, Object>> highItems = alarm == null ? List.of() : List.of(alarm);
        return Map.of(
                "total", total,
                "latest", Map.of("total", total, "items", highItems),
                "high", Map.of("total", total, "items", highItems),
                "medium", Map.of("total", 0, "items", List.of()),
                "low", Map.of("total", 0, "items", List.of()));
    }
}
