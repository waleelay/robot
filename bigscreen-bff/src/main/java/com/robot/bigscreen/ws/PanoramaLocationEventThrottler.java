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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/** 按浏览器会话和机器人合并高频位置事件，每秒只下发最新位置。 */
@Component
public class PanoramaLocationEventThrottler {

    private static final Logger log = LoggerFactory.getLogger(PanoramaLocationEventThrottler.class);

    private static final String LOCATION_EVENT = "panorama.device.location.changed";
    private static final long INTERVAL_NANOS = 1_000_000_000L;

    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    /**
     * 位置节流使用的单调时间源，便于精确测试间隔。
     */
    private final LongSupplier nanoTime;
    /**
     * 按浏览器连接和机器人隔离位置节流状态，移除后旧任务不得投递。
     */
    private final Map<EventKey, LocationState> states = new ConcurrentHashMap<>();

    /**
     * 初始化 PanoramaLocationEventThrottler，保存所需依赖及初始运行状态。
     *
     * @param objectMapper JSON 编解码器
     * @param taskScheduler 后台任务调度器
     */
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

    /**
     * 按连接与机器人合并高频位置更新；定位失效等必要事件按既有规则及时投递。
     *
     * @param sessionId 会话 ID
     * @param payload 消息载荷
     * @param publisher 向目标浏览器或身份分组投递消息的回调
     */
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
                log.debug("位置事件延后投递 协议=websocket 阶段=节流 结果=延后 业务类型=位置 会话标识={} 机器人标识={} 原因码=等待GIS转换结果",
                        sessionId, locationEvent.robotId());
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
                log.debug("位置事件已合并 协议=websocket 阶段=节流 结果=合并 业务类型=位置 会话标识={} 机器人标识={} 是否含地理坐标={} 原因码=触发频率限制",
                        sessionId, locationEvent.robotId(), locationEvent.hasGis());
                scheduleIfNeeded(key, state, now);
            }
        }
        if (immediatePayload != null) {
            publisher.accept(immediatePayload);
        }
    }

    /**
     * 移除指定连接的位置节流状态；已调度任务执行时因状态不再匹配而跳过投递。
     * @param sessionId 会话 ID
     */
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

    /**
     * 按浏览器会话和机器人隔离位置节流状态。
     *
     * @param sessionId 会话 ID
     * @param robotId 机器人 ID
     */
    private record EventKey(String sessionId, String robotId) {
    }

    /**
     * 位置事件的设备、定位有效性、GIS 可用性及地图信息。
     *
     * @param robotId 机器人 ID
     * @param localizationInvalid 定位是否失效；失效事件需要及时清除旧位置
     * @param hasGis 位置事件是否带有有效 GIS 经纬度
     * @param mapId 所属地图 ID
     */
    private record LocationEvent(
            String robotId,
            boolean localizationInvalid,
            boolean hasGis,
            String mapId) {
    }

    /** 保留最新位置载荷及节流任务，协调 GIS 和 SLAM 更新的发送时机。 */
    private static final class LocationState {
        /**
         * 上次发布的单调时间，单位纳秒；最小值表示尚未发布。
         */
        private long lastPublishedNanos = Long.MIN_VALUE;
        /**
         * 是否已安排一个待发送任务，防止重复调度。
         */
        private boolean scheduled;
        /**
         * 当前窗口中等待发布的最新位置消息正文。
         */
        private String pendingPayload;
        /**
         * 待发送消息是否包含有效 GIS 坐标。
         */
        private boolean pendingHasGis;
        /**
         * 等待 GIS 优先消息完成后再发送的 SLAM 位置。
         */
        private String deferredSlamPayload;
        /**
         * 当前浏览器连接的位置消息投递回调。
         */
        private Consumer<String> publisher;
        /**
         * 最近位置所属地图，切图时重置节流判断。
         */
        private String mapId = "";
    }
}
