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
import org.springframework.scheduling.TaskScheduler;

class PanoramaTaskEventRefresherTest {

    @Test
    void debouncesAndPublishesOnlyChangedTasks() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> running = Map.of("taskId", 1L, "workflowInstanceId", 9001L, "status", "running");
        Map<String, Object> completed = Map.of("taskId", 1L, "workflowInstanceId", 9001L, "status", "completed");
        when(panoramaService.taskEventSnapshot())
                .thenReturn(Map.of("items", List.of(running), "convergencePending", false))
                .thenReturn(Map.of("items", List.of(running), "convergencePending", false))
                .thenReturn(Map.of("items", List.of(completed), "convergencePending", false))
                .thenReturn(Map.of("items", List.of(), "convergencePending", false));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(jobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaTaskEventRefresher refresher = new PanoramaTaskEventRefresher(panoramaService, objectMapper, scheduler);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add);
        refresher.requestRefresh("browser-a", null, events::add);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        jobs.getValue().run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(1).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(2).run();
        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(3).run();

        assertThat(events).hasSize(3);
        JsonNode event = objectMapper.readTree(events.get(1));
        assertThat(event.path("event").asText()).isEqualTo("panorama.task.changed");
        assertThat(event.path("data").path("taskId").isNumber()).isTrue();
        assertThat(event.path("data").path("taskId").asLong()).isEqualTo(1L);
        assertThat(event.path("data").path("task").path("taskId").asLong()).isEqualTo(1L);
        assertThat(event.path("data").path("task").path("workflowInstanceId").asLong()).isEqualTo(9001L);
        assertThat(event.path("data").path("task").path("status").asText()).isEqualTo("completed");
        JsonNode removed = objectMapper.readTree(events.get(2));
        assertThat(removed.path("data").path("taskId").asLong()).isEqualTo(1L);
        assertThat(removed.path("data").path("changeType").asText()).isEqualTo("REMOVE");
        assertThat(removed.path("data").has("task")).isFalse();
    }

    @Test
    void retriesFailedRefreshAndPreparingConvergenceWithBoundedBackoff() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Map<String, Object> running = Map.of("taskId", 1L, "status", "running");
        when(panoramaService.taskEventSnapshot())
                .thenThrow(new IllegalStateException("busy"))
                .thenReturn(Map.of("items", List.of(running), "convergencePending", true))
                .thenReturn(Map.of("items", List.of(running), "convergencePending", false));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(jobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaTaskEventRefresher refresher = new PanoramaTaskEventRefresher(
                panoramaService, new ObjectMapper(), scheduler);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("browser-a", null, events::add);
        jobs.getAllValues().get(0).run();
        jobs.getAllValues().get(1).run();
        jobs.getAllValues().get(2).run();

        verify(panoramaService, times(3)).taskEventSnapshot();
        verify(scheduler, times(3)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(events).hasSize(1);
    }
}
