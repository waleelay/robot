package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaService;
import com.robot.bigscreen.panorama.StatsPart;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

@Component
public class PanoramaStatsEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaStatsEventRefresher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEBOUNCE_MILLIS = 500;

    private final PanoramaService panoramaService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final Map<String, RefreshState> states = new ConcurrentHashMap<>();

    public PanoramaStatsEventRefresher(
            PanoramaService panoramaService,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler) {
        this.panoramaService = panoramaService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    public void requestRefresh(
            String sessionId,
            Authentication authentication,
            Consumer<String> publisher,
            Set<StatsPart> parts) {
        RefreshState state = states.computeIfAbsent(sessionId, ignored -> new RefreshState());
        state.authentication = authentication;
        state.publisher = publisher;
        if (parts == null || parts.isEmpty()) {
            parts = EnumSet.allOf(StatsPart.class);
        }
        state.mergeParts(parts);
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
            Set<StatsPart> parts = state.drainParts();
            Map<String, Object> snapshot = withAuthentication(
                    state.authentication, () -> panoramaService.statsSnapshot(parts));
            snapshot = stableSnapshot(state.previousSnapshot, snapshot);
            Map<String, Object> merged = new LinkedHashMap<>(state.previousSnapshot);
            merged.putAll(snapshot);
            if (!Objects.equals(state.previousSnapshot, merged)) {
                state.previousSnapshot = merged;
                Consumer<String> publisher = state.publisher;
                if (publisher != null) {
                    publisher.accept(event(merged));
                }
            }
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图统计事件失败，会话={}", sessionId, exception);
        } finally {
            state.scheduled.set(false);
            if (states.get(sessionId) == state && state.dirty.get()) {
                scheduleIfNeeded(sessionId, state);
            }
        }
    }

    private Map<String, Object> stableSnapshot(Map<String, Object> previousSnapshot, Map<String, Object> snapshot) {
        if (previousDeviceTotal(previousSnapshot) <= 0 || deviceTotal(snapshot) != 0) {
            return snapshot;
        }
        Map<String, Object> stable = new LinkedHashMap<>(snapshot);
        if (previousSnapshot.containsKey("deviceStats")) {
            stable.put("deviceStats", previousSnapshot.get("deviceStats"));
        }
        if (previousSnapshot.containsKey("deviceTypeStats")) {
            stable.put("deviceTypeStats", previousSnapshot.get("deviceTypeStats"));
        }
        return stable;
    }

    private int previousDeviceTotal(Map<String, Object> snapshot) {
        return deviceTotal(snapshot);
    }

    private int deviceTotal(Map<String, Object> snapshot) {
        if (!(snapshot.get("deviceStats") instanceof Map<?, ?> deviceStats)) {
            return -1;
        }
        Object total = deviceStats.get("total");
        if (total instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private String event(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "panorama.stats.changed",
                    "timestamp", TIME_FORMATTER.format(LocalDateTime.now()),
                    "data", snapshot));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize panorama statistics event", exception);
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
        private final Set<StatsPart> pendingParts = EnumSet.noneOf(StatsPart.class);
        private volatile Authentication authentication;
        private volatile Consumer<String> publisher;
        private volatile Map<String, Object> previousSnapshot = Map.of();

        private void mergeParts(Set<StatsPart> parts) {
            synchronized (pendingParts) {
                pendingParts.addAll(parts);
            }
        }

        private Set<StatsPart> drainParts() {
            synchronized (pendingParts) {
                Set<StatsPart> parts = EnumSet.copyOf(pendingParts);
                pendingParts.clear();
                return parts.isEmpty() ? EnumSet.allOf(StatsPart.class) : parts;
            }
        }
    }
}
