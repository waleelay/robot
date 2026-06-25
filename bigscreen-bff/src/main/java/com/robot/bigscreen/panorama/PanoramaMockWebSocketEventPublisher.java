package com.robot.bigscreen.panorama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.ws.BigscreenWebSocketBridgeHandler;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PanoramaMockWebSocketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaMockWebSocketEventPublisher.class);
    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BigscreenWebSocketBridgeHandler webSocketBridgeHandler;
    private final ObjectMapper objectMapper;
    private long tick;

    public PanoramaMockWebSocketEventPublisher(
            BigscreenWebSocketBridgeHandler webSocketBridgeHandler,
            ObjectMapper objectMapper) {
        this.webSocketBridgeHandler = webSocketBridgeHandler;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            initialDelayString = "${bigscreen.mock.websocket.initial-delay-ms:3000}",
            fixedDelayString = "${bigscreen.mock.websocket.fixed-delay-ms:5000}")
    public void publishMockEvents() {
        long currentTick = tick++;
        for (Map<String, Object> event : events(currentTick)) {
            try {
                webSocketBridgeHandler.broadcastToBrowserSessions(objectMapper.writeValueAsString(event));
            } catch (JsonProcessingException exception) {
                log.warn("Failed to serialize panorama mock websocket event event={}", event.get("event"), exception);
            }
        }
    }

    private List<Map<String, Object>> events(long currentTick) {
        return List.of(
                deviceStatusChanged(currentTick),
                deviceLocationChanged(currentTick),
                taskChanged(currentTick),
                alarmChanged(currentTick),
                statsChanged(currentTick));
    }

    private Map<String, Object> deviceStatusChanged(long currentTick) {
        double speed = Math.round((0.4 + (currentTick % 4) * 0.1) * 10.0) / 10.0;
        return event("panorama.device.status.changed", object(
                "robotId", "robot-001",
                "status", "online",
                "battery", 90 + (int) (currentTick % 10),
                "controlMode", "MANUAL",
                "speed", speed));
    }

    private Map<String, Object> deviceLocationChanged(long currentTick) {
        double offset = (currentTick % 6) * 0.00003;
        return event("panorama.device.location.changed", object(
                "robotId", "robot-001",
                "location", object(
                        "lng", 106.03655278081857 + offset,
                        "lat", 30.7478613352993 + offset,
                        "altitude", null,
                        "address", "A区主干道",
                        "updatedAt", now())));
    }

    private Map<String, Object> taskChanged(long currentTick) {
        boolean running = currentTick % 2 == 0;
        return event("panorama.task.changed", object(
                "taskId", "task-001",
                "task", object(
                        "taskId", "task-001",
                        "name", "A区-夜间巡逻",
                        "status", running ? "running" : "paused",
                        "statusName", running ? "执行中" : "暂停中",
                        "timeRange", "20:00-22:00",
                        "currentLocation", "A区主干道")));
    }

    private Map<String, Object> alarmChanged(long currentTick) {
        boolean created = currentTick % 3 == 0;
        return event("panorama.alarm.changed", object(
                "alarmId", "alarm-001",
                "summary", alarmSummary(currentTick),
                "alarm", object(
                        "alarmId", "alarm-001",
                        "title", "发生火灾",
                        "category", "BUSINESS",
                        "categoryName", "业务告警",
                        "level", "HIGH",
                        "levelName", "高风险",
                        "eventTime", "2023-08-01 10:00:00",
                        "location", "A区仓库",
                        "robotId", "robot-001",
                        "deviceName", "R1轮式机器人",
                        "taskId", "task-002",
                        "taskName", "A区-仓库复核",
                        "status", created ? "unhandled" : "handling",
                        "snapshotUrl", object(
                                "visible", "",
                                "thermal", "",
                                "front", ""))));
    }

    private Map<String, Object> statsChanged(long currentTick) {
        int online = 18 + (int) (currentTick % 2);
        return event("panorama.stats.changed", object(
                "deviceStats", object(
                        "total", 22,
                        "online", online,
                        "fault", 2,
                        "offline", 22 - online - 2),
                "deviceTypeStats", List.of(
                        object("type", "ROBOT_DOG", "name", "机器狗", "count", 8),
                        object("type", "HUMANOID_ROBOT", "name", "机器人", "count", 6),
                        object("type", "WHEELED_ROBOT", "name", "轮式车", "count", 8)),
                "alarmStats", object(
                        "high", 5,
                        "medium", 5,
                        "low", 5),
                "alarmSummary", alarmSummary(currentTick)));
    }

    private Map<String, Object> alarmSummary(long currentTick) {
        int handled = 18 + (int) (currentTick % 3);
        int unhandled = (int) (currentTick % 2);
        int totalToday = 50 + (int) (currentTick % 5);
        int handleRate = totalToday == 0 ? 100 : (int) Math.round(handled * 100.0 / totalToday);
        return object(
                "totalToday", totalToday,
                "handled", handled,
                "unhandled", unhandled,
                "handleRate", handleRate,
                "handleRateText", handleRate + "%");
    }

    private Map<String, Object> event(String event, Map<String, Object> data) {
        return object(
                "event", event,
                "timestamp", now(),
                "data", data);
    }

    private String now() {
        return OffsetDateTime.now(CHINA_ZONE).format(DATE_TIME_FORMATTER);
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
