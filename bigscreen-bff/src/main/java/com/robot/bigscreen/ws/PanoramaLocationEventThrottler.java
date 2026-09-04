package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
            if (!locationEvent.mapId().isBlank() && !Objects.equals(state.mapId, locationEvent.mapId())) {
                state.mapId = locationEvent.mapId();
                state.pendingPayload = null;
                state.pendingHasGis = false;
                state.deferredSlamPayload = null;
            }
            if (!locationEvent.localizationInvalid() && state.pendingHasGis && !locationEvent.hasGis()) {
                state.deferredSlamPayload = payload;
                return;
            }
            if (locationEvent.hasGis()) {
                state.deferredSlamPayload = null;
            }
            if (locationEvent.localizationInvalid()
                    || state.lastPublishedNanos == Long.MIN_VALUE
                    || (!state.scheduled && now - state.lastPublishedNanos >= INTERVAL_NANOS)) {
                state.pendingPayload = null;
                state.pendingHasGis = false;
                state.deferredSlamPayload = null;
                state.lastPublishedNanos = now;
                immediatePayload = payload;
            } else {
                state.pendingPayload = payload;
                state.pendingHasGis = locationEvent.hasGis();
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
            boolean publishedGis = state.pendingHasGis;
            state.pendingPayload = null;
            state.pendingHasGis = false;
            state.lastPublishedNanos = now;
            publisher = state.publisher;
            if (publishedGis && state.deferredSlamPayload != null) {
                state.pendingPayload = state.deferredSlamPayload;
                state.deferredSlamPayload = null;
                scheduleIfNeeded(key, state, now);
            }
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
            JsonNode location = root.path("data").path("location");
            boolean hasGis = validGis(location.path("lng"), location.path("lat"));
            return new LocationEvent(
                    robotId,
                    localized.isBoolean() && !localized.asBoolean(),
                    hasGis,
                    location.path("mapId").asText(""));
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean validGis(JsonNode longitude, JsonNode latitude) {
        return longitude.isNumber() && latitude.isNumber()
                && longitude.doubleValue() >= -180 && longitude.doubleValue() <= 180
                && latitude.doubleValue() >= -90 && latitude.doubleValue() <= 90;
    }

    private record EventKey(String sessionId, String robotId) {
    }

    private record LocationEvent(
            String robotId,
            boolean localizationInvalid,
            boolean hasGis,
            String mapId) {
    }

    private static final class LocationState {
        private long lastPublishedNanos = Long.MIN_VALUE;
        private boolean scheduled;
        private String pendingPayload;
        private boolean pendingHasGis;
        private String deferredSlamPayload;
        private Consumer<String> publisher;
        private String mapId = "";
    }
}
