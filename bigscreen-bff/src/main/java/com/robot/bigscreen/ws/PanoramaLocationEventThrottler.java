package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** 按浏览器会话和机器人合并高频位置事件，每秒只下发最新位置。 */
@Component
public class PanoramaLocationEventThrottler {

    private static final String LOCATION_EVENT = "panorama.device.location.changed";
    private static final long INTERVAL_NANOS = 1_000_000_000L;

    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final LongSupplier nanoTime;
    private final Map<EventKey, LocationState> states = new ConcurrentHashMap<>();

    @Autowired
    public PanoramaLocationEventThrottler(ObjectMapper objectMapper, TaskScheduler taskScheduler) {
        this(objectMapper, taskScheduler, System::nanoTime);
    }

    PanoramaLocationEventThrottler(
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            LongSupplier nanoTime) {
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.nanoTime = nanoTime;
    }

    public void publish(String sessionId, String payload, Consumer<String> publisher) {
        LocationEvent locationEvent = locationEvent(payload);
        if (locationEvent == null) {
            publisher.accept(payload);
            return;
        }

        EventKey key = new EventKey(sessionId, locationEvent.robotId());
        LocationState state = states.computeIfAbsent(key, ignored -> new LocationState());
        String immediatePayload = null;
        synchronized (state) {
            long now = nanoTime.getAsLong();
            state.publisher = publisher;
            if (locationEvent.localizationInvalid()
                    || state.lastPublishedNanos == Long.MIN_VALUE
                    || (!state.scheduled && now - state.lastPublishedNanos >= INTERVAL_NANOS)) {
                state.pendingPayload = null;
                state.lastPublishedNanos = now;
                immediatePayload = payload;
            } else {
                state.pendingPayload = payload;
                scheduleIfNeeded(key, state, now);
            }
        }
        if (immediatePayload != null) {
            publisher.accept(immediatePayload);
        }
    }

    public void remove(String sessionId) {
        states.keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }

    private void scheduleIfNeeded(EventKey key, LocationState state, long now) {
        if (state.scheduled) {
            return;
        }
        state.scheduled = true;
        long elapsed = Math.max(0, now - state.lastPublishedNanos);
        long delayNanos = Math.max(0, INTERVAL_NANOS - elapsed);
        try {
            taskScheduler.schedule(() -> publishPending(key, state), Instant.now().plusNanos(delayNanos));
        } catch (RuntimeException exception) {
            state.scheduled = false;
            throw exception;
        }
    }

    private void publishPending(EventKey key, LocationState state) {
        String payload;
        Consumer<String> publisher;
        synchronized (state) {
            state.scheduled = false;
            if (states.get(key) != state || state.pendingPayload == null) {
                return;
            }
            long now = nanoTime.getAsLong();
            if (now - state.lastPublishedNanos < INTERVAL_NANOS) {
                scheduleIfNeeded(key, state, now);
                return;
            }
            payload = state.pendingPayload;
            state.pendingPayload = null;
            state.lastPublishedNanos = now;
            publisher = state.publisher;
        }
        if (publisher != null) {
            publisher.accept(payload);
        }
    }

    private LocationEvent locationEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!LOCATION_EVENT.equals(root.path("event").asText())) {
                return null;
            }
            String robotId = root.path("data").path("robotId").asText("");
            if (robotId.isBlank()) {
                return null;
            }
            JsonNode localized = root.path("data").path("location").path("localized");
            return new LocationEvent(robotId, localized.isBoolean() && !localized.asBoolean());
        } catch (Exception exception) {
            return null;
        }
    }

    private record EventKey(String sessionId, String robotId) {
    }

    private record LocationEvent(String robotId, boolean localizationInvalid) {
    }

    private static final class LocationState {
        private long lastPublishedNanos = Long.MIN_VALUE;
        private boolean scheduled;
        private String pendingPayload;
        private Consumer<String> publisher;
    }
}
