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
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 收到管理端任务失效通知后，重新查询权威任务快照并推送变化项。 */
@Component
public class PanoramaTaskEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaTaskEventRefresher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEBOUNCE_MILLIS = 300;
    private static final long[] RETRY_DELAYS_MILLIS = {1000, 2000, 4000, 8000};

    private final PanoramaService panoramaService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final Map<String, RefreshState> states = new ConcurrentHashMap<>();

    public PanoramaTaskEventRefresher(
            PanoramaService panoramaService,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler) {
        this.panoramaService = panoramaService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    public void requestRefresh(String sessionId, Authentication authentication, Consumer<String> publisher, boolean followChanges) {
        RefreshState state = states.computeIfAbsent(sessionId, ignored -> new RefreshState());
        synchronized (state) {
            state.authentication = authentication;
            state.publisher = publisher;
            state.followChanges |= followChanges;
            state.retryCount = 0;
            state.dirty = true;
            scheduleIfNeeded(sessionId, state, DEBOUNCE_MILLIS);
        }
    }

    public void remove(String sessionId) {
        RefreshState state = states.remove(sessionId);
        if (state != null) {
            synchronized (state) {
                if (state.pending != null) state.pending.cancel(false);
                state.scheduledAt = null;
            }
        }
    }

    // 调用方持有 state 锁；新事件可提前旧退避任务，但不延后已有去抖任务。
    private void scheduleIfNeeded(String sessionId, RefreshState state, long delayMillis) {
        Instant due = Instant.now().plusMillis(delayMillis);
        if (state.running || (state.scheduledAt != null && !due.isBefore(state.scheduledAt))) return;
        if (state.pending != null) state.pending.cancel(false);
        state.scheduledAt = due;
        state.pending = taskScheduler.schedule(() -> refresh(sessionId, state, due), due);
    }

    private void refresh(String sessionId, RefreshState state, Instant due) {
        synchronized (state) {
            if (states.get(sessionId) != state || !due.equals(state.scheduledAt)) return;
            state.scheduledAt = null;
            state.pending = null;
            state.running = true;
            state.dirty = false;
        }
        boolean retry = false;
        try {
            Map<String, Object> response = withAuthentication(state.authentication, panoramaService::taskEventSnapshot);
            if (states.get(sessionId) != state) return;
            boolean complete = Boolean.TRUE.equals(response.get("tasksComplete"));
            Consumer<String> publisher = state.publisher;
            Map<String, Map<String, Object>> current = index(tasks(response));
            Object plans = response.get("plans");
            boolean plansChanged = !Objects.equals(state.previousPlans, plans);
            if (plansChanged || (complete && !Objects.equals(state.previousTasks, current))) {
                withAuthentication(state.authentication, () -> {
                    panoramaService.invalidateTaskOverview();
                    return null;
                });
            }
            boolean collectionChanged = complete && (!state.collectionInitialized
                    || !state.previousTasks.keySet().equals(current.keySet()));
            if (complete) {
                current.forEach((taskId, task) -> {
                    if (!Objects.equals(state.previousTasks.get(taskId), task)) {
                        publisher.accept(event(task, task.get("taskId")));
                    }
                });
                state.previousTasks.forEach((taskId, task) -> {
                    if (!current.containsKey(taskId)) publisher.accept(removeEvent(task.get("taskId")));
                });
                state.previousTasks = current;
            }
            // 计划快照首次就绪或内容变化才通知列表，与实例摘要查询是否成功无关。
            if (plansChanged || collectionChanged) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("scopes", List.of("PLAN", "EXECUTION"));
                // 首次完整快照也校准空集合；摘要失败时绝不把空集合当成删除。
                if (collectionChanged) data.put("taskIds", List.copyOf(current.keySet()));
                publisher.accept(objectMapper.writeValueAsString(Map.of(
                        "event", "management.task.invalidated",
                        "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                        "data", data)));
                state.previousPlans = plans;
                if (complete) state.collectionInitialized = true;
            }
            // 通知不含目标计划版本，不能用其他任务的变化推断收敛；事件按固定上限复查。
            // 初次连接的正常快照只查一次，查询失败和准备中仍有界重试。
            retry = state.followChanges || !complete || Boolean.TRUE.equals(response.get("convergencePending"));
        } catch (Exception exception) {
            log.warn("刷新全景地图任务事件失败，会话={}", sessionId, exception);
            retry = true;
        } finally {
            synchronized (state) {
                long delay = DEBOUNCE_MILLIS;
                // 查询期间的新事件优先，不能被当前请求的退避延后。
                if (!state.dirty && retry && state.retryCount < RETRY_DELAYS_MILLIS.length) {
                    state.dirty = true;
                    delay = RETRY_DELAYS_MILLIS[state.retryCount++];
                }
                state.running = false;
                if (states.get(sessionId) == state && state.dirty) scheduleIfNeeded(sessionId, state, delay);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tasks(Map<String, Object> response) {
        Object items = response.get("items");
        return items instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private Map<String, Map<String, Object>> index(List<Map<String, Object>> tasks) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> task : tasks) {
            Object taskId = task.get("taskId");
            if (taskId != null) {
                result.put(String.valueOf(taskId), new LinkedHashMap<>(task));
            }
        }
        return result;
    }

    private String event(Map<String, Object> task, Object taskId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.task.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", Map.of("taskId", taskId, "task", task)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize panorama task event", exception);
        }
    }

    private String removeEvent(Object taskId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.task.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", Map.of("taskId", taskId, "changeType", "REMOVE")));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化全景地图任务移除事件失败", exception);
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

    private static final class RefreshState {
        private ScheduledFuture<?> pending;
        private Instant scheduledAt;
        private boolean running;
        private boolean dirty;
        private volatile Authentication authentication;
        private volatile Consumer<String> publisher;
        private volatile Map<String, Map<String, Object>> previousTasks = Map.of();
        private volatile int retryCount;
        private volatile boolean followChanges;
        private Object previousPlans;
        private boolean collectionInitialized;
    }
}
