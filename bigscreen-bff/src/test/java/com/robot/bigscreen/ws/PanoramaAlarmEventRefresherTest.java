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

class PanoramaAlarmEventRefresherTest {

    @Test
    void debouncesInvalidationsAndPublishesOnlyChangedAlarms() throws Exception {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> unhandled = Map.of("alarmId", "alarm-1", "level", "HIGH", "status", "unhandled");
        Map<String, Object> handled = Map.of("alarmId", "alarm-1", "level", "HIGH", "status", "handled");
        when(panoramaService.alarms())
                .thenReturn(response(unhandled, 1, 0))
                .thenReturn(response(unhandled, 1, 0))
                .thenReturn(response(handled, 1, 1));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(jobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, objectMapper, scheduler);
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
        assertThat(event.path("event").asText()).isEqualTo("panorama.alarm.changed");
        assertThat(event.path("data").path("alarmId").asText()).isEqualTo("alarm-1");
        assertThat(event.path("data").path("alarm").path("status").asText()).isEqualTo("handled");
        assertThat(event.path("data").path("summary").path("handled").asInt()).isEqualTo(1);
    }

    private Map<String, Object> response(Map<String, Object> alarm, int total, int handled) {
        return Map.of("alarms", Map.of(
                "summary", Map.of("totalToday", total, "handled", handled, "unhandled", total - handled),
                "high", Map.of("items", List.of(alarm)),
                "medium", Map.of("items", List.of()),
                "low", Map.of("items", List.of())));
    }
}
