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
import java.util.concurrent.atomic.AtomicBoolean;
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

    public void requestRefresh(String sessionId, Authentication authentication, Consumer<String> publisher) {
        RefreshState state = states.computeIfAbsent(sessionId, ignored -> new RefreshState());
        state.authentication = authentication;
        state.publisher = publisher;
        state.dirty.set(true);
        scheduleIfNeeded(sessionId, state);
    }

    public void remove(String sessionId) {
        states.remove(sessionId);
    }

    private void scheduleIfNeeded(String sessionId, RefreshState state) {
        if (state.scheduled.compareAndSet(false, true)) {
            taskScheduler.schedule(() -> refresh(sessionId, state), Instant.now().plusMillis(DEBOUNCE_MILLIS));
        }
    }

    private void refresh(String sessionId, RefreshState state) {
        if (states.get(sessionId) != state) {
            return;
        }
        state.dirty.set(false);
        try {
            Map<String, Object> response = withAuthentication(state.authentication, panoramaService::tasks);
            Map<String, Map<String, Object>> current = index(tasks(response));
            Consumer<String> publisher = state.publisher;
            if (publisher != null) {
                current.forEach((taskId, task) -> {
                    if (!Objects.equals(state.previousTasks.get(taskId), task)) {
                        publisher.accept(event(task, task.get("taskId")));
                    }
                });
                state.previousTasks.forEach((taskId, task) -> {
                    if (!current.containsKey(taskId)) {
                        publisher.accept(removeEvent(task.get("taskId")));
                    }
                });
            }
            state.previousTasks = current;
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图任务事件失败，会话={}", sessionId, exception);
        } finally {
            state.scheduled.set(false);
            if (states.get(sessionId) == state && state.dirty.get()) {
                scheduleIfNeeded(sessionId, state);
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
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean dirty = new AtomicBoolean();
        private volatile Authentication authentication;
        private volatile Consumer<String> publisher;
        private volatile Map<String, Map<String, Object>> previousTasks = Map.of();
    }
}
