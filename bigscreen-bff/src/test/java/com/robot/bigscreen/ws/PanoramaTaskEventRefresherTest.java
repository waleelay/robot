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
    private static class Fixture {
        final PanoramaService service = mock(PanoramaService.class);
        final TaskScheduler scheduler = mock(TaskScheduler.class);
        final ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        final ArgumentCaptor<Instant> due = ArgumentCaptor.forClass(Instant.class);
        final List<String> events = new ArrayList<>();
        final PanoramaTaskEventRefresher refresher = new PanoramaTaskEventRefresher(service, new ObjectMapper(), scheduler);
        Fixture() { when(scheduler.schedule(jobs.capture(), due.capture())).thenReturn(null); }
        void request(boolean follow) { refresher.requestRefresh("browser", null, events::add, follow); }
        void run() { jobs.getValue().run(); }
        long notifications() { return events.stream().filter(value -> value.contains("management.task.invalidated")).count(); }
    }

    private static Map<String, Object> snapshot(List<Map<String, Object>> tasks) {
        return Map.of("plans", tasks, "items", tasks, "tasksComplete", true, "convergencePending", false);
    }

    @Test
    void initialConnectionReadsOnceAndNotifiesEvenForEmptySnapshot() throws Exception {
        Fixture f = new Fixture();
        when(f.service.taskEventSnapshot()).thenReturn(snapshot(List.of()));
        f.request(false);
        f.run();
        verify(f.service).taskEventSnapshot();
        assertThat(f.jobs.getAllValues()).hasSize(1);
        assertThat(f.notifications()).isEqualTo(1);
        JsonNode data = new ObjectMapper().readTree(f.events.get(0)).path("data");
        assertThat(data.has("taskIds")).isTrue();
        assertThat(data.path("taskIds").size()).isZero();
    }

    @Test
    void debouncesChangesAndDoesNotNotifyForIdenticalSnapshots() throws Exception {
        Fixture f = new Fixture();
        Map<String, Object> task = Map.of("taskId", 1L, "status", "running");
        when(f.service.taskEventSnapshot()).thenReturn(snapshot(List.of(task)), snapshot(List.of(task)), snapshot(List.of()));
        f.request(true);
        f.request(true);
        assertThat(f.jobs.getAllValues()).hasSize(1);
        f.run();
        f.run();
        assertThat(f.events).hasSize(2);
        f.run();
        assertThat(f.notifications()).isEqualTo(2);
        JsonNode removed = new ObjectMapper().readTree(f.events.get(2));
        assertThat(removed.path("data").path("changeType").asText()).isEqualTo("REMOVE");
    }

    @Test
    void unrelatedTaskChangeDoesNotStopWaitingForDelayedTask() {
        Fixture f = new Fixture();
        Map<String, Object> waiting = Map.of("taskId", 1L, "status", "waiting");
        Map<String, Object> other = Map.of("taskId", 2L, "status", "running");
        when(f.service.taskEventSnapshot()).thenReturn(
                snapshot(List.of(waiting)), snapshot(List.of(waiting, other)),
                snapshot(List.of(Map.of("taskId", 1L, "status", "running"), other)));
        f.request(true);
        f.run();
        f.run();
        assertThat(f.jobs.getAllValues()).hasSize(3);
        f.run();
        assertThat(f.events).anyMatch(value -> value.contains("running") && value.contains("\"taskId\":1"));
    }

    @Test
    void unchangedEventStopsAtFiveReadsAndNotifiesListOnlyOnce() {
        Fixture f = new Fixture();
        when(f.service.taskEventSnapshot()).thenReturn(snapshot(List.of(Map.of("taskId", 1L, "status", "waiting"))));
        f.request(true);
        for (int i = 0; i < 5; i++) f.run();
        verify(f.service, times(5)).taskEventSnapshot();
        assertThat(f.jobs.getAllValues()).hasSize(5);
        assertThat(f.notifications()).isEqualTo(1);
        f.request(true);
        f.refresher.remove("browser");
        f.run();
        verify(f.service, times(5)).taskEventSnapshot();
    }

    @Test
    void instanceFailurePreservesCardsAndStillNotifiesChangedPlans() {
        Fixture f = new Fixture();
        Map<String, Object> task = Map.of("taskId", 1L, "status", "running");
        when(f.service.taskEventSnapshot()).thenReturn(snapshot(List.of(task)), Map.of(
                "plans", List.of(Map.of("id", 1L, "planName", "new")), "items", List.of(), "tasksComplete", false));
        f.request(false);
        f.run();
        f.request(true);
        f.run();
        assertThat(f.notifications()).isEqualTo(2);
        assertThat(f.events).noneMatch(value -> value.contains("REMOVE"));
        assertThat(f.events).hasSize(3);
    }

    @Test
    void firstCompleteSnapshotAfterFailureStillSendsCollection() throws Exception {
        Fixture f = new Fixture();
        when(f.service.taskEventSnapshot()).thenReturn(
                Map.of("plans", List.of(), "items", List.of(), "tasksComplete", false), snapshot(List.of()));
        f.request(false);
        f.run();
        assertThat(new ObjectMapper().readTree(f.events.get(0)).path("data").has("taskIds")).isFalse();
        f.run();
        assertThat(new ObjectMapper().readTree(f.events.get(1)).path("data").has("taskIds")).isTrue();
    }

    @Test
    void retriesFailedInitialQueryButStopsAfterRecovery() {
        Fixture f = new Fixture();
        when(f.service.taskEventSnapshot()).thenThrow(new IllegalStateException("busy")).thenReturn(snapshot(List.of()));
        f.request(false);
        f.run();
        assertThat(f.events).isEmpty();
        f.run();
        assertThat(f.jobs.getAllValues()).hasSize(2);
        assertThat(f.notifications()).isEqualTo(1);
    }

    @Test
    void newEventAdvancesBackoffAndObsoleteJobDoesNotQuery() {
        Fixture f = new Fixture();
        when(f.service.taskEventSnapshot()).thenReturn(snapshot(List.of()));
        f.request(true);
        f.run();
        Runnable oldRetry = f.jobs.getValue();
        Instant oldDue = f.due.getValue();
        f.request(true);
        assertThat(f.due.getValue()).isBefore(oldDue);
        oldRetry.run();
        verify(f.service).taskEventSnapshot();
        f.run();
        verify(f.service, times(2)).taskEventSnapshot();
    }
}
