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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

class PanoramaAlarmEventRefresherTest {

    @Test
    void deduplicatesSameEventAcrossSessionsWithoutDiscardingInFlightResult() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of(
                "items", List.of(Map.of("alarmId", "alarm-1"))));
        when(panoramaService.alarmEventSnapshot()).thenReturn(Map.of("total", 0));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("identity-a", null, events::add, "management:1001");
        refresher.requestRefresh("identity-a", null, events::add, "management:1001");
        verify(taskExecutor, times(2)).execute(any(Runnable.class));
        jobs.getAllValues().get(0).run();
        jobs.getAllValues().get(1).run();

        verify(panoramaService).actionableWorkflowAlarms();
        verify(panoramaService).alarmEventSnapshot();
        assertThat(events).anyMatch(event -> event.contains("alarm-1"));
    }

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

    @Test
    void workflowReadyInvalidationDuringQueryRunsAgainImmediately() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        Map<String, Object> actionable = Map.of("alarmId", "alarm-1", "workflowActionable", true);
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        AtomicReference<PanoramaAlarmEventRefresher> reference = new AtomicReference<>();
        List<String> events = new ArrayList<>();
        when(panoramaService.actionableWorkflowAlarms())
                .thenAnswer(ignored -> {
                    reference.get().requestRefresh("identity-a", null, events::add);
                    jobs.getAllValues().get(2).run();
                    return Map.of("items", List.of());
                })
                .thenReturn(Map.of("items", List.of(actionable)));
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        reference.set(refresher);

        refresher.requestRefresh("identity-a", null, events::add);
        jobs.getAllValues().get(0).run();

        verify(panoramaService, times(2)).actionableWorkflowAlarms();
        verify(scheduler, times(0)).schedule(any(Runnable.class), any(Instant.class));
        List<String> workflowEvents = events.stream()
                .filter(value -> value.contains("workflow-alarms"))
                .toList();
        assertThat(workflowEvents).hasSize(1);
        assertThat(workflowEvents.get(0)).contains("alarm-1");
    }

    @Test
    void connectionSnapshotRetriesFailureWithoutPublishingFalseEmptySnapshot() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(panoramaService.actionableWorkflowAlarms())
                .thenThrow(new IllegalStateException("下游超时"))
                .thenReturn(Map.of("items", List.of()));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> scheduledJobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        when(scheduler.schedule(scheduledJobs.capture(), any(Instant.class))).thenReturn(null);
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestSnapshot("identity-a", null, events::add);
        jobs.getAllValues().get(0).run();
        assertThat(events).isEmpty();

        scheduledJobs.getValue().run();
        jobs.getAllValues().get(1).run();

        verify(panoramaService, times(2)).actionableWorkflowAlarms();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).contains("\"panorama.workflow-alarms.changed\"");
    }

    @Test
    void connectionSnapshotPublishesAuthoritativeEmptyWithoutRetry() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of("items", List.of()));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        List<String> events = new ArrayList<>();

        refresher.requestSnapshot("identity-a", null, events::add);
        jobs.getValue().run();

        verify(panoramaService).actionableWorkflowAlarms();
        verify(scheduler, times(0)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(events).hasSize(1);
    }

    @Test
    void connectionSnapshotTargetsNewSessionWithoutRebroadcastingToExistingSessions() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        Map<String, Object> actionable = Map.of("alarmId", "alarm-1", "workflowActionable", true);
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of("items", List.of(actionable)));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        List<String> realtimeEvents = new ArrayList<>();
        List<String> newSessionEvents = new ArrayList<>();

        refresher.requestRefresh("identity-a", null, realtimeEvents::add);
        jobs.getAllValues().get(0).run();
        refresher.requestSnapshot("identity-a", null, newSessionEvents::add);
        jobs.getAllValues().get(2).run();

        assertThat(realtimeEvents).hasSize(1);
        assertThat(newSessionEvents).hasSize(1);
        verify(panoramaService, times(2)).actionableWorkflowAlarms();
    }

    @Test
    void failedBrowserDeliveryDoesNotAdvanceWorkflowSnapshot() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ScheduledFuture<?> pending = mock(ScheduledFuture.class);
        Map<String, Object> actionable = Map.of("alarmId", "alarm-1", "workflowActionable", true);
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of("items", List.of(actionable)));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        org.mockito.Mockito.doReturn(pending)
                .when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        List<String> delivered = new ArrayList<>();

        refresher.requestRefresh("identity-a", null, ignored -> false);
        jobs.getAllValues().get(0).run();
        refresher.requestRefresh("identity-a", null, delivered::add);
        jobs.getAllValues().get(2).run();

        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0)).contains("alarm-1");
        verify(pending).cancel(false);
    }

    @Test
    void connectionSnapshotAdvancesBackoffWithoutStoppingExistingConvergence() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ScheduledFuture<?> pending = mock(ScheduledFuture.class);
        when(panoramaService.actionableWorkflowAlarms()).thenReturn(Map.of("items", List.of()));
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> scheduledJobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        org.mockito.Mockito.doReturn(pending)
                .when(scheduler).schedule(scheduledJobs.capture(), any(Instant.class));
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);

        refresher.requestRefresh("identity-a", null, ignored -> true);
        jobs.getAllValues().get(0).run();
        Runnable obsoleteRetry = scheduledJobs.getValue();

        refresher.requestSnapshot("identity-a", null, ignored -> true);
        verify(pending).cancel(false);
        jobs.getAllValues().get(2).run();
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));

        obsoleteRetry.run();
        jobs.getAllValues().get(3).run();
        verify(panoramaService, times(2)).actionableWorkflowAlarms();

        scheduledJobs.getValue().run();
        jobs.getAllValues().get(4).run();
        verify(panoramaService, times(3)).actionableWorkflowAlarms();
    }

    @Test
    void doesNotPublishWorkflowQueryThatCompletesAfterIdentityRemoval() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        AtomicReference<PanoramaAlarmEventRefresher> reference = new AtomicReference<>();
        when(panoramaService.actionableWorkflowAlarms()).thenAnswer(ignored -> {
            reference.get().remove("identity-a");
            return Map.of("items", List.of(Map.of("alarmId", "alarm-1")));
        });
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        reference.set(refresher);
        List<String> events = new ArrayList<>();

        refresher.requestSnapshot("identity-a", null, events::add);
        jobs.getValue().run();

        assertThat(events).isEmpty();
    }

    @Test
    void doesNotPublishAlarmQueryThatCompletesAfterIdentityRemoval() {
        PanoramaService panoramaService = mock(PanoramaService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ArgumentCaptor<Runnable> jobs = ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.doNothing().when(taskExecutor).execute(jobs.capture());
        AtomicReference<PanoramaAlarmEventRefresher> reference = new AtomicReference<>();
        when(panoramaService.alarmEventSnapshot()).thenAnswer(ignored -> {
            reference.get().remove("identity-a");
            return snapshot(Map.of("alarmId", "alarm-1"), 1);
        });
        PanoramaAlarmEventRefresher refresher = new PanoramaAlarmEventRefresher(
                panoramaService, new ObjectMapper(), scheduler, taskExecutor);
        reference.set(refresher);
        List<String> events = new ArrayList<>();

        refresher.requestRefresh("identity-a", null, events::add);
        jobs.getAllValues().get(1).run();

        assertThat(events).isEmpty();
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
