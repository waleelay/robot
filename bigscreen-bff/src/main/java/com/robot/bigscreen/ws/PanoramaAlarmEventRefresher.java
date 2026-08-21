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

/** 收到管理端告警失效通知后，重新查询权威告警快照并推送变化项。 */
@Component
public class PanoramaAlarmEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaAlarmEventRefresher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEBOUNCE_MILLIS = 300;

    private final PanoramaService panoramaService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final Map<String, RefreshState> states = new ConcurrentHashMap<>();

    public PanoramaAlarmEventRefresher(
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
            Map<String, Object> response = withAuthentication(state.authentication, panoramaService::alarms);
            Map<String, Object> alarms = map(response.get("alarms"));
            Map<String, Object> summary = map(alarms.get("summary"));
            Map<String, Map<String, Object>> current = index(alarms);
            Consumer<String> publisher = state.publisher;
            if (publisher != null) {
                current.forEach((alarmId, alarm) -> {
                    if (!Objects.equals(state.previousAlarms.get(alarmId), alarm)) {
                        publisher.accept(event(alarmId, alarm, summary));
                    }
                });
                state.previousAlarms.keySet().stream()
                        .filter(alarmId -> !current.containsKey(alarmId))
                        .forEach(alarmId -> publisher.accept(removeEvent(alarmId, summary)));
            }
            state.previousAlarms = current;
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图告警事件失败，会话={}", sessionId, exception);
        } finally {
            state.scheduled.set(false);
            if (states.get(sessionId) == state && state.dirty.get()) {
                scheduleIfNeeded(sessionId, state);
            }
        }
    }

    private Map<String, Map<String, Object>> index(Map<String, Object> alarms) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String level : List.of("high", "medium", "low")) {
            for (Map<String, Object> alarm : maps(map(alarms.get(level)).get("items"))) {
                Object alarmId = alarm.get("alarmId");
                if (alarmId != null) {
                    result.put(String.valueOf(alarmId), new LinkedHashMap<>(alarm));
                }
            }
        }
        return result;
    }

    private String event(String alarmId, Map<String, Object> alarm, Map<String, Object> summary) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("alarmId", alarmId);
            data.put("alarm", alarm);
            data.put("summary", summary);
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.alarm.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", data));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化全景地图告警事件失败", exception);
        }
    }

    private String removeEvent(String alarmId, Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.alarm.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", Map.of(
                            "alarmId", alarmId,
                            "changeType", "REMOVE",
                            "summary", summary)));
        } catch (Exception exception) {
            throw new IllegalStateException("序列化全景地图告警移除事件失败", exception);
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
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
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
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final AtomicBoolean dirty = new AtomicBoolean();
        private volatile Authentication authentication;
        private volatile Consumer<String> publisher;
        private volatile Map<String, Map<String, Object>> previousAlarms = Map.of();
    }
}
