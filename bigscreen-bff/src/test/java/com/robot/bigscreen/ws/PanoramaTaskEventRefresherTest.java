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
        when(panoramaService.tasks())
                .thenReturn(Map.of("items", List.of(running)))
                .thenReturn(Map.of("items", List.of(running)))
                .thenReturn(Map.of("items", List.of(completed)));
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

        assertThat(events).hasSize(2);
        JsonNode event = objectMapper.readTree(events.get(1));
        assertThat(event.path("event").asText()).isEqualTo("panorama.task.changed");
        assertThat(event.path("data").path("taskId").isNumber()).isTrue();
        assertThat(event.path("data").path("taskId").asLong()).isEqualTo(1L);
        assertThat(event.path("data").path("task").path("taskId").asLong()).isEqualTo(1L);
        assertThat(event.path("data").path("task").path("workflowInstanceId").asLong()).isEqualTo(9001L);
        assertThat(event.path("data").path("task").path("status").asText()).isEqualTo("completed");
    }
}
