package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 查询普通告警变化及可处置工作流告警快照，并在人工任务未就绪时短时收敛。 */
@Component
public class PanoramaAlarmEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaAlarmEventRefresher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long[] RETRY_DELAYS_MILLIS = {300, 600, 1200, 2400};
    private static final long CONVERGENCE_TIMEOUT_MILLIS = 5000;

    private final PanoramaService panoramaService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final TaskExecutor taskExecutor;
    private final Map<String, RefreshState> states = new ConcurrentHashMap<>();

    public PanoramaAlarmEventRefresher(
            PanoramaService panoramaService,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.panoramaService = panoramaService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.taskExecutor = taskExecutor;
    }

    public void requestSnapshot(String sessionId, Authentication authentication, Consumer<String> publisher) {
        RefreshState state = state(sessionId);
        synchronized (state) {
            state.authentication = authentication;
            state.publisher = publisher;
            state.workflowFailureRetryDeadlineMillis =
                    System.currentTimeMillis() + CONVERGENCE_TIMEOUT_MILLIS;
            state.workflowRetryCount = 0;
            state.forceWorkflowPublish = true;
            state.workflowDirty = true;
            scheduleWorkflowIfNeeded(sessionId, state, 0);
        }
    }

    public void requestRefresh(String sessionId, Authentication authentication, Consumer<String> publisher) {
        RefreshState state = state(sessionId);
        synchronized (state) {
            state.authentication = authentication;
            state.publisher = publisher;
            state.retryDeadlineMillis = System.currentTimeMillis() + CONVERGENCE_TIMEOUT_MILLIS;
            state.workflowFailureRetryDeadlineMillis = state.retryDeadlineMillis;
            state.workflowRetryCount = 0;
            state.workflowDirty = true;
            scheduleWorkflowIfNeeded(sessionId, state, 0);
        }
        state.alarmsDirty.set(true);
        scheduleAlarmsIfNeeded(sessionId, state);
    }

    private RefreshState state(String sessionId) {
        return states.computeIfAbsent(sessionId, ignored -> new RefreshState());
    }

    public void remove(String sessionId) {
        RefreshState state = states.remove(sessionId);
        if (state != null) {
            synchronized (state) {
                if (state.workflowPending != null) state.workflowPending.cancel(false);
                state.workflowScheduledAt = null;
            }
        }
    }

    // 新事件可取消并提前旧退避任务；调用方持有 state 锁。
    private void scheduleWorkflowIfNeeded(String sessionId, RefreshState state, long delayMillis) {
        Instant due = Instant.now().plusMillis(delayMillis);
        if (state.workflowRunning
                || (state.workflowScheduledAt != null && !due.isBefore(state.workflowScheduledAt))) {
            return;
        }
        if (state.workflowPending != null) state.workflowPending.cancel(false);
        state.workflowScheduledAt = due;
        Runnable dispatch = () -> taskExecutor.execute(() -> refreshWorkflow(sessionId, state, due));
        if (delayMillis == 0) {
            dispatch.run();
        } else {
            state.workflowPending = taskScheduler.schedule(dispatch, due);
        }
    }

    private void refreshWorkflow(String sessionId, RefreshState state, Instant due) {
        synchronized (state) {
            if (states.get(sessionId) != state || !due.equals(state.workflowScheduledAt)) return;
            state.workflowScheduledAt = null;
            state.workflowPending = null;
            state.workflowRunning = true;
            state.workflowDirty = false;
        }
        boolean retry = false;
        try {
            List<Map<String, Object>> workflowItems = maps(withAuthentication(
                    state.authentication, panoramaService::actionableWorkflowAlarms).get("items"));
            if (states.get(sessionId) != state) return;
            Map<String, Map<String, Object>> currentWorkflowAlarms = index(workflowItems);
            boolean snapshotPublished = state.workflowSnapshotPublished;
            boolean changed = !snapshotPublished
                    || !Objects.equals(state.previousWorkflowAlarms, currentWorkflowAlarms);
            boolean publish = state.forceWorkflowPublish || changed;
            state.forceWorkflowPublish = false;
            Consumer<String> publisher = state.publisher;
            if (publisher != null && publish) {
                publisher.accept(workflowSnapshotEvent(workflowItems));
            }
            state.previousWorkflowAlarms = currentWorkflowAlarms;
            state.workflowSnapshotPublished = true;
            retry = (!changed || (!snapshotPublished && currentWorkflowAlarms.isEmpty()))
                    && System.currentTimeMillis() < state.retryDeadlineMillis;
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图工作流告警失败，身份={}", sessionId, exception);
            retry = System.currentTimeMillis() < state.workflowFailureRetryDeadlineMillis;
        } finally {
            synchronized (state) {
                long delay = 0;
                if (!state.workflowDirty && retry
                        && state.workflowRetryCount < RETRY_DELAYS_MILLIS.length) {
                    state.workflowDirty = true;
                    delay = jitteredDelay(RETRY_DELAYS_MILLIS[state.workflowRetryCount++]);
                }
                state.workflowRunning = false;
                if (states.get(sessionId) == state && state.workflowDirty) {
                    scheduleWorkflowIfNeeded(sessionId, state, delay);
                } else {
                    state.workflowRetryCount = 0;
                }
            }
        }
    }

    private long jitteredDelay(long delayMillis) {
        long spread = Math.max(1L, delayMillis / 5L);
        return delayMillis + ThreadLocalRandom.current().nextLong(-spread, spread + 1L);
    }

    private void scheduleAlarmsIfNeeded(String sessionId, RefreshState state) {
        if (state.alarmsScheduled.compareAndSet(false, true)) {
            taskExecutor.execute(() -> refreshAlarms(sessionId, state));
        }
    }

    private void refreshAlarms(String sessionId, RefreshState state) {
        if (states.get(sessionId) != state) {
            return;
        }
        state.alarmsDirty.set(false);
        try {
            Map<String, Object> alarms = withAuthentication(
                    state.authentication, panoramaService::alarmEventSnapshot);
            if (states.get(sessionId) != state) return;
            boolean changed = !Objects.equals(state.previousAlarms, alarms);
            Consumer<String> publisher = state.publisher;
            if (publisher != null && changed) {
                publisher.accept(alarmSnapshotEvent(alarms));
            }
            state.previousAlarms = alarms;
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图普通告警失败，身份={}", sessionId, exception);
        } finally {
            state.alarmsScheduled.set(false);
            if (states.get(sessionId) == state && state.alarmsDirty.get()) {
                scheduleAlarmsIfNeeded(sessionId, state);
            }
        }
    }

    private Map<String, Map<String, Object>> index(List<Map<String, Object>> alarms) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> alarm : alarms) {
            Object alarmId = alarm.get("alarmId");
            if (alarmId != null) {
                result.put(String.valueOf(alarmId), new LinkedHashMap<>(alarm));
            }
        }
        return result;
    }

    private String alarmSnapshotEvent(Map<String, Object> alarms) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.alarms.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", alarms));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化全景地图告警快照失败", exception);
        }
    }

    private String workflowSnapshotEvent(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.workflow-alarms.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", Map.of("total", items.size(), "items", items)));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化工作流告警快照事件失败", exception);
        }
    }

    private <T> T withAuthentication(Authentication authentication, java.util.function.Supplier<T> supplier) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static final class RefreshState {
        private final AtomicBoolean alarmsScheduled = new AtomicBoolean();
        private final AtomicBoolean alarmsDirty = new AtomicBoolean();
        private ScheduledFuture<?> workflowPending;
        private Instant workflowScheduledAt;
        private boolean workflowRunning;
        private boolean workflowDirty;
        private int workflowRetryCount;
        private boolean forceWorkflowPublish;
        private volatile Authentication authentication;
        private volatile Consumer<String> publisher;
        private volatile Map<String, Object> previousAlarms = Map.of();
        private volatile Map<String, Map<String, Object>> previousWorkflowAlarms = Map.of();
        private volatile long retryDeadlineMillis;
        private volatile long workflowFailureRetryDeadlineMillis;
        private volatile boolean workflowSnapshotPublished;
    }
}
