package com.robot.control.trajectory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.messaging.EquipmentControlCommandPublisher;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 按大屏实际观看目标查询并定向推送设备任务轨迹。 */
@Component
public class TrajectoryCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryCoordinator.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final long SUMMARY_INTERVAL_MILLIS = 1_000;
    private static final long RETRY_MILLIS = 3_000;
    private static final long REQUEST_TIMEOUT_MILLIS = 3_000;
    private static final int FULL_PAGE_SIZE = 500;

    private final ObjectMapper objectMapper;
    private final EquipmentControlCommandPublisher commandPublisher;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final TaskScheduler scheduler;
    private final Map<String, Watch> watches = new HashMap<>();
    private final Map<Target, Runner> runners = new HashMap<>();
    private final Map<String, Long> taskIdsByRobot = new HashMap<>();
    private final Map<String, Pending> pendingByCommand = new HashMap<>();

    public TrajectoryCoordinator(
            ObjectMapper objectMapper,
            EquipmentControlCommandPublisher commandPublisher,
            MediaWebSocketPublisher webSocketPublisher,
            TaskScheduler scheduler) {
        this.objectMapper = objectMapper;
        this.commandPublisher = commandPublisher;
        this.webSocketPublisher = webSocketPublisher;
        this.scheduler = scheduler;
    }

    /** 原子替换一个 WebSocket 会话当前观看的完整目标集合。 */
    public synchronized void sync(WebSocketSession session, Object value) {
        Set<Target> targets = parseTargets(value);
        Watch previous = watches.get(session.getId());
        Set<Target> added = new HashSet<>(targets);
        if (previous != null) added.removeAll(previous.targets);
        watches.put(session.getId(), new Watch(session, targets));
        reconcile();
        added.forEach(target -> restoreForNewWatcher(runners.get(target), session.getId()));
    }

    /** WebSocket 断开后立即释放该会话的观看目标。 */
    public synchronized void removeSession(WebSocketSession session) {
        watches.remove(session.getId());
        runners.values().forEach(runner -> {
            runner.restoreSessions.remove(session.getId());
            runner.resetPendingSessions.remove(session.getId());
        });
        reconcile();
    }

    /** 接收设备状态中最近一次明确上报的 taskInstanceId。 */
    public synchronized void observeTaskInstance(String robotId, Object value) {
        Long taskInstanceId = positiveLong(value);
        if (robotId == null || robotId.isBlank() || taskInstanceId == null
                || Objects.equals(taskIdsByRobot.put(robotId, taskInstanceId), taskInstanceId)) {
            return;
        }
        runners.values().stream()
                .filter(runner -> runner.target.robotId().equals(robotId))
                .forEach(runner -> {
                    if (runner.boundTaskId != null) {
                        stop(runner);
                        runner.boundTaskId = null;
                        runner.candidateTaskId = null;
                        return;
                    }
                    if (runner.stopped) return;
                    invalidate(runner);
                    runner.candidateTaskId = taskInstanceId;
                    runner.rejectedTaskId = null;
                    runner.stopped = false;
                    resetCursor(runner);
                    schedule(runner, Query.PROBE, 0);
                });
    }

    /** 接收 trajectory/snapshot 响应。 */
    public synchronized void handleSnapshot(String topic, String json) {
        try {
            String robotId = robotIdFromSnapshotTopic(topic);
            Map<String, Object> report = objectMapper.readValue(json, MAP_TYPE);
            String commandId = string(report.get("commandId"));
            Pending pending = pendingByCommand.get(commandId);
            if (pending == null || !pending.target.robotId().equals(robotId)) {
                return;
            }
            Runner runner = runners.get(pending.target);
            if (runner == null || runner.version != pending.version || !commandId.equals(runner.pendingCommandId)
                    || !Objects.equals(positiveLong(report.get("taskInstanceId")), pending.taskInstanceId)
                    || !pending.format.equals(string(report.get("format")))) {
                return;
            }
            pendingByCommand.remove(commandId);
            runner.pendingCommandId = null;
            String status = string(report.get("status")).toLowerCase();
            if (runner.boundTaskId == null) {
                handleProbe(runner, pending, status);
                return;
            }
            if ("error".equals(status) || "not_found".equals(status)) {
                retry(runner, pending.query);
                return;
            }
            if (!"recording".equals(status) && !"stopped".equals(status)) {
                retry(runner, pending.query);
                return;
            }
            if (pending.query == Query.SUMMARY) {
                if ("stopped".equals(status)) {
                    stop(runner);
                } else {
                    handleSummary(runner, map(report.get("summary")));
                }
            } else {
                handleFull(runner, report, pending.query, "stopped".equals(status));
            }
        } catch (Exception exception) {
            log.warn("处理设备轨迹响应失败，主题={} 载荷字节数={}", topic,
                    json == null ? 0 : json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, exception);
        }
    }

    private void handleProbe(Runner runner, Pending pending, String status) {
        if (!Objects.equals(runner.candidateTaskId, pending.taskInstanceId)) {
            return;
        }
        if ("recording".equals(status)) {
            runner.boundTaskId = pending.taskInstanceId;
            runner.candidateTaskId = null;
            prepareRestore(runner, watchingSessionIds(runner.target), null);
            schedule(runner, Query.RESTORE, 0);
        } else if ("stopped".equals(status)) {
            runner.rejectedTaskId = pending.taskInstanceId;
            runner.candidateTaskId = null;
        } else {
            schedule(runner, Query.PROBE, RETRY_MILLIS);
        }
    }

    private void handleFull(Runner runner, Map<String, Object> report, Query query, boolean stopped) {
        Map<String, Object> summary = map(report.get("summary"));
        Double startTime = finiteDouble(summary.get("startTime"));
        List<Map<String, Object>> points = absolutePoints(report.get("points"), startTime);
        boolean hasMore = Boolean.TRUE.equals(report.get("hasMore"));
        Double previousCursor = runner.lastTimestamp;
        if (startTime == null || (hasMore && points.isEmpty())) {
            retryFull(runner, query);
            return;
        }
        if (!points.isEmpty()) {
            double nextCursor = (double) points.get(points.size() - 1).get("timestamp");
            if (previousCursor != null && nextCursor <= previousCursor && hasMore) {
                retryFull(runner, query);
                return;
            }
            runner.lastTimestamp = Math.max(previousCursor == null ? nextCursor : previousCursor, nextCursor);
        }
        Map<String, Object> currentPose = timedPose(summary.get("currentPose"), finiteDouble(summary.get("lastUpdateTime")));
        if (query == Query.RESTORE) {
            emitRestore(runner, points, currentPose, hasMore);
        } else {
            emit(runner, "APPEND", points, currentPose);
        }
        runner.resetSent = true;
        updateSummaryState(runner, summary, currentPose);
        if (stopped) {
            stop(runner);
        } else if (hasMore) {
            schedule(runner, query, 0);
        } else {
            schedule(runner, Query.SUMMARY, 0);
        }
    }

    private void handleSummary(Runner runner, Map<String, Object> summary) {
        Long total = nonNegativeLong(summary.get("totalPoints"));
        Double startTime = finiteDouble(summary.get("startTime"));
        Double lastUpdateTime = finiteDouble(summary.get("lastUpdateTime"));
        Map<String, Object> currentPose = timedPose(summary.get("currentPose"), lastUpdateTime);
        if (total == null || startTime == null || currentPose == null) {
            restartRestore(runner);
            return;
        }
        if (runner.startTime == null || runner.totalPoints == null
                || !Objects.equals(runner.startTime, startTime) || total < runner.totalPoints) {
            restartRestore(runner);
            return;
        }
        long added = total - runner.totalPoints;
        if (added > 1) {
            schedule(runner, Query.GAP, 0);
            return;
        }
        if (added == 1) {
            if (runner.lastTimestamp != null && lastUpdateTime <= runner.lastTimestamp) {
                restartRestore(runner);
                return;
            }
            emit(runner, "APPEND", List.of(currentPose), currentPose);
            runner.lastTimestamp = lastUpdateTime;
        } else if (!samePose(runner.currentPose, currentPose)) {
            emit(runner, "APPEND", List.of(), currentPose);
        }
        updateSummaryState(runner, summary, currentPose);
        schedule(runner, Query.SUMMARY, SUMMARY_INTERVAL_MILLIS);
    }

    private void updateSummaryState(Runner runner, Map<String, Object> summary, Map<String, Object> currentPose) {
        Long total = nonNegativeLong(summary.get("totalPoints"));
        Double start = finiteDouble(summary.get("startTime"));
        if (total != null) runner.totalPoints = total;
        if (start != null) runner.startTime = start;
        if (currentPose != null) runner.currentPose = currentPose;
    }

    private void restartRestore(Runner runner) {
        prepareRestore(runner, watchingSessionIds(runner.target), null);
        schedule(runner, Query.RESTORE, RETRY_MILLIS);
    }

    private void retryFull(Runner runner, Query query) {
        if (query == Query.RESTORE) resetRestore(runner);
        schedule(runner, query, RETRY_MILLIS);
    }

    private void retry(Runner runner, Query query) {
        if (query == Query.RESTORE) resetRestore(runner);
        schedule(runner, query, RETRY_MILLIS);
    }

    private void stop(Runner runner) {
        if (runner.stopped) return;
        runner.stopped = true;
        invalidate(runner);
        emit(runner, "STOPPED", null, null);
    }

    private void reconcile() {
        Map<String, Set<Target>> desiredByRobot = new HashMap<>();
        watches.values().forEach(watch -> watch.targets.forEach(target ->
                desiredByRobot.computeIfAbsent(target.robotId(), ignored -> new HashSet<>()).add(target)));
        Set<Target> desired = new HashSet<>();
        desiredByRobot.forEach((robotId, targets) -> {
            if (targets.size() == 1) {
                desired.add(targets.iterator().next());
            } else {
                log.warn("TRAJECTORY_WATCH_CONFLICT：同一机器人存在不同执行轮次，robotId={} targets={}", robotId, targets);
            }
        });
        new ArrayList<>(runners.entrySet()).forEach(entry -> {
            if (!desired.contains(entry.getKey())) {
                invalidate(entry.getValue());
                runners.remove(entry.getKey());
            }
        });
        desired.forEach(target -> {
            if (runners.containsKey(target)) return;
            Runner runner = new Runner(target);
            runner.candidateTaskId = taskIdsByRobot.get(target.robotId());
            runners.put(target, runner);
            if (runner.candidateTaskId != null) schedule(runner, Query.PROBE, 0);
        });
    }

    private void schedule(Runner runner, Query query, long delayMillis) {
        if (runner.stopped || runners.get(runner.target) != runner) return;
        if (runner.scheduled != null) runner.scheduled.cancel(false);
        long version = runner.version;
        runner.scheduled = scheduler.schedule(() -> execute(runner.target, version, query),
                Instant.now().plusMillis(delayMillis));
    }

    private synchronized void execute(Target target, long version, Query query) {
        Runner runner = runners.get(target);
        if (runner == null || runner.version != version || runner.stopped || runner.pendingCommandId != null) return;
        Long taskInstanceId = query == Query.PROBE ? runner.candidateTaskId : runner.boundTaskId;
        if (taskInstanceId == null || Objects.equals(taskInstanceId, runner.rejectedTaskId)) return;
        String commandId = "trajectory-" + UUID.randomUUID();
        String format = query == Query.PROBE || query == Query.SUMMARY ? "summary" : "full";
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("commandId", commandId);
        command.put("taskInstanceId", taskInstanceId);
        command.put("format", format);
        if ("full".equals(format)) {
            command.put("maxPoints", FULL_PAGE_SIZE);
            if ((query == Query.GAP || runner.resetSent) && runner.lastTimestamp != null) {
                command.put("sinceTimestamp", runner.lastTimestamp);
            }
        }
        runner.pendingCommandId = commandId;
        Pending pending = new Pending(target, runner.version, taskInstanceId, format, query);
        pendingByCommand.put(commandId, pending);
        try {
            commandPublisher.publishTrajectoryQuery(target.robotId(), command);
        } catch (RuntimeException exception) {
            pendingByCommand.remove(commandId);
            runner.pendingCommandId = null;
            log.warn("发布轨迹查询失败，robotId={} workflowInstanceId={}", target.robotId(), target.workflowInstanceId(), exception);
            retry(runner, query);
            return;
        }
        scheduler.schedule(() -> timeout(commandId), Instant.now().plusMillis(REQUEST_TIMEOUT_MILLIS));
    }

    private synchronized void timeout(String commandId) {
        Pending pending = pendingByCommand.remove(commandId);
        if (pending == null) return;
        Runner runner = runners.get(pending.target);
        if (runner == null || runner.version != pending.version || !commandId.equals(runner.pendingCommandId)) return;
        runner.pendingCommandId = null;
        retry(runner, pending.query);
    }

    private void invalidate(Runner runner) {
        runner.version++;
        if (runner.scheduled != null) runner.scheduled.cancel(false);
        if (runner.pendingCommandId != null) pendingByCommand.remove(runner.pendingCommandId);
        runner.pendingCommandId = null;
    }

    private void resetCursor(Runner runner) {
        runner.startTime = null;
        runner.totalPoints = null;
        runner.lastTimestamp = null;
        runner.currentPose = null;
        runner.resetSent = false;
    }

    private void restoreForNewWatcher(Runner runner, String sessionId) {
        if (runner == null || runner.boundTaskId == null || runner.stopped) return;
        if (!runner.restoreSessions.isEmpty()) {
            runner.restoreSessions.add(sessionId);
            if (!runner.resetSent) {
                runner.resetPendingSessions.add(sessionId);
                return;
            }
        } else {
            runner.restoreCutoff = runner.lastTimestamp;
            runner.restoreSessions.add(sessionId);
        }
        invalidate(runner);
        resetRestore(runner);
        schedule(runner, Query.RESTORE, 0);
    }

    private void prepareRestore(Runner runner, Collection<String> sessionIds, Double cutoff) {
        resetCursor(runner);
        runner.restoreCutoff = cutoff;
        runner.restoreSessions.clear();
        runner.restoreSessions.addAll(sessionIds);
        runner.resetPendingSessions.clear();
        runner.resetPendingSessions.addAll(sessionIds);
    }

    private void resetRestore(Runner runner) {
        resetCursor(runner);
        runner.resetPendingSessions.clear();
        runner.resetPendingSessions.addAll(runner.restoreSessions);
    }

    private Set<String> watchingSessionIds(Target target) {
        Set<String> sessionIds = new HashSet<>();
        watches.forEach((sessionId, watch) -> {
            if (watch.targets.contains(target) && watch.session.isOpen()) sessionIds.add(sessionId);
        });
        return sessionIds;
    }

    private void emitRestore(Runner runner, List<Map<String, Object>> points,
            Map<String, Object> currentPose, boolean hasMore) {
        TextMessage resetMessage = null;
        TextMessage restoreAppendMessage = null;
        TextMessage deltaAppendMessage = null;
        List<Map<String, Object>> added = null;
        for (Watch watch : watches.values()) {
            String sessionId = watch.session.getId();
            if (!watch.targets.contains(runner.target) || !watch.session.isOpen()) continue;
            if (runner.restoreSessions.contains(sessionId)) {
                if (runner.resetPendingSessions.remove(sessionId)) {
                    if (resetMessage == null) resetMessage = message(runner, "RESET", points, currentPose);
                    if (resetMessage != null) send(watch.session, resetMessage);
                } else {
                    if (restoreAppendMessage == null) {
                        restoreAppendMessage = message(runner, "APPEND", points, currentPose);
                    }
                    if (restoreAppendMessage != null) send(watch.session, restoreAppendMessage);
                }
                continue;
            }
            if (added == null) {
                added = points.stream()
                        .filter(point -> runner.restoreCutoff == null
                                || (double) point.get("timestamp") > runner.restoreCutoff)
                        .toList();
            }
            if (!added.isEmpty() || !hasMore) {
                if (deltaAppendMessage == null) {
                    deltaAppendMessage = message(runner, "APPEND", added, currentPose);
                }
                if (deltaAppendMessage != null) send(watch.session, deltaAppendMessage);
            }
        }
        if (!hasMore) {
            runner.restoreSessions.clear();
            runner.resetPendingSessions.clear();
            runner.restoreCutoff = null;
        }
    }

    private void emit(Runner runner, String action, Collection<Map<String, Object>> points, Map<String, Object> currentPose) {
        TextMessage message = message(runner, action, points, currentPose);
        if (message == null) return;
        for (Watch watch : watches.values()) {
            if (!watch.targets.contains(runner.target) || !watch.session.isOpen()) continue;
            send(watch.session, message);
        }
    }

    private TextMessage message(Runner runner, String action,
            Collection<Map<String, Object>> points, Map<String, Object> currentPose) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("robotId", runner.target.robotId());
        data.put("workflowInstanceId", runner.target.workflowInstanceId());
        data.put("action", action);
        if (points != null) data.put("points", points);
        if (currentPose != null) data.put("currentPose", currentPose);
        Map<String, Object> event = Map.of(
                "event", "robot.trajectory.changed",
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "data", data);
        try {
            return new TextMessage(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            log.warn("序列化轨迹事件失败，robotId={}", runner.target.robotId(), exception);
            return null;
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        try {
            webSocketPublisher.send(session, message);
        } catch (IOException exception) {
            log.debug("轨迹事件发送失败，WebSocket 会话={}", session.getId(), exception);
        }
    }

    private Set<Target> parseTargets(Object value) {
        Map<String, Object> payload = map(value);
        Object rawTargets = payload.get("targets");
        if (!(rawTargets instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("trajectory.watch.sync.targets 必须是数组");
        }
        Set<Target> targets = new HashSet<>();
        Set<String> robots = new HashSet<>();
        for (Object item : collection) {
            Map<String, Object> target = map(item);
            String robotId = string(target.get("robotId"));
            String workflowInstanceId = string(target.get("workflowInstanceId"));
            if (robotId.isBlank() || workflowInstanceId.isBlank() || !robots.add(robotId)) {
                throw new IllegalArgumentException("轨迹观看目标字段不完整或机器人重复");
            }
            targets.add(new Target(robotId, workflowInstanceId));
        }
        return targets;
    }

    private List<Map<String, Object>> absolutePoints(Object value, Double startTime) {
        if (!(value instanceof Collection<?> collection) || startTime == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            Map<String, Object> point = map(item);
            Double t = finiteDouble(point.get("t"));
            Map<String, Object> timed = timedPose(point, t == null ? null : startTime + t);
            if (timed != null) result.add(timed);
        }
        return result;
    }

    private Map<String, Object> timedPose(Object value, Double timestamp) {
        Map<String, Object> pose = map(value);
        Double x = finiteDouble(pose.get("x"));
        Double y = finiteDouble(pose.get("y"));
        if (timestamp == null || x == null || y == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("x", x);
        result.put("y", y);
        Double yaw = finiteDouble(pose.get("yaw"));
        if (yaw != null) result.put("yaw", yaw);
        return result;
    }

    private boolean samePose(Map<String, Object> first, Map<String, Object> second) {
        return first != null && second != null
                && Objects.equals(first.get("x"), second.get("x"))
                && Objects.equals(first.get("y"), second.get("y"))
                && Objects.equals(first.get("yaw"), second.get("yaw"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private String robotIdFromSnapshotTopic(String topic) {
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (parts.length != 6 || !"eiop".equals(parts[0]) || !"v1".equals(parts[1])
                || !"edge".equals(parts[2]) || parts[3].isBlank()
                || !"trajectory".equals(parts[4]) || !"snapshot".equals(parts[5])) {
            throw new IllegalArgumentException("无效的轨迹响应 topic：" + topic);
        }
        return parts[3];
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long positiveLong(Object value) {
        try {
            long number = Long.parseLong(string(value));
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long nonNegativeLong(Object value) {
        try {
            long number = Long.parseLong(string(value));
            return number >= 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double finiteDouble(Object value) {
        try {
            double number = Double.parseDouble(string(value));
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum Query { PROBE, RESTORE, SUMMARY, GAP }

    private record Target(String robotId, String workflowInstanceId) {}
    private record Watch(WebSocketSession session, Set<Target> targets) {}
    private record Pending(Target target, long version, Long taskInstanceId, String format, Query query) {}

    private static final class Runner {
        private final Target target;
        private long version;
        private Long candidateTaskId;
        private Long rejectedTaskId;
        private Long boundTaskId;
        private Double startTime;
        private Long totalPoints;
        private Double lastTimestamp;
        private Map<String, Object> currentPose;
        private Double restoreCutoff;
        private final Set<String> restoreSessions = new HashSet<>();
        private final Set<String> resetPendingSessions = new HashSet<>();
        private boolean resetSent;
        private boolean stopped;
        private String pendingCommandId;
        private ScheduledFuture<?> scheduled;

        private Runner(Target target) {
            this.target = target;
        }
    }
}
