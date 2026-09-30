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
    /**
     * 以浏览器连接 ID 保存订阅目标；在协调器监视锁内更新。
     */
    private final Map<String, Watch> watches = new HashMap<>();
    /**
     * 按机器人和工作流目标保存轨迹同步状态；在协调器监视锁内访问。
     */
    private final Map<Target, Runner> runners = new HashMap<>();
    /**
     * 机器人最近明确上报的设备任务实例 ID，用于发现轨迹任务切换。
     */
    private final Map<String, Long> taskIdsByRobot = new HashMap<>();
    /**
     * 以命令 ID 关联在途轨迹查询及其版本，拒绝迟到或不匹配响应。
     */
    private final Map<String, Pending> pendingByCommand = new HashMap<>();

    /**
     * 初始化 TrajectoryCoordinator，保存所需依赖及初始运行状态。
     *
     * @param objectMapper JSON 编解码器
     * @param commandPublisher 向设备发布统一控制 MQTT 指令的组件
     * @param webSocketPublisher 向已连接客户端投递业务事件的组件
     * @param scheduler 后台任务调度器
     */
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

    /**
     * 原子替换一个 WebSocket 会话当前观看的完整目标集合。
     *
     * @param session WebSocket 会话
     * @param value 浏览器提交的完整轨迹订阅目标集合
     */
    public synchronized void sync(WebSocketSession session, Object value) {
        Set<Target> targets = parseTargets(value);
        Watch previous = watches.get(session.getId());
        Set<Target> added = new HashSet<>(targets);
        if (previous != null) {
            added.removeAll(previous.targets);
        }
        watches.put(session.getId(), new Watch(session, targets));
        log.info("轨迹订阅已接受 协议=websocket 方向=入站 阶段=订阅 结果=已接受 业务类型=轨迹 会话标识={} 目标数量={} 新增数量={}",
                session.getId(), targets.size(), added.size());
        reconcile();
        added.forEach(target -> restoreForNewWatcher(runners.get(target), session.getId()));
    }

    /**
     * WebSocket 断开后立即释放该会话的观看目标。
     *
     * @param session WebSocket 会话
     */
    public synchronized void removeSession(WebSocketSession session) {
        watches.remove(session.getId());
        runners.values().forEach(runner -> {
            runner.restoreSessions.remove(session.getId());
            runner.resetPendingSessions.remove(session.getId());
        });
        reconcile();
    }

    /**
     * 接收设备最近一次明确上报的 taskInstanceId。
     *
     * @param robotId 机器人 ID
     * @param value 边缘状态中的当前设备任务实例标识
     */
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
                    if (runner.stopped) {
                        return;
                    }
                    invalidate(runner);
                    runner.candidateTaskId = taskInstanceId;
                    runner.rejectedTaskId = null;
                    runner.stopped = false;
                    resetCursor(runner);
                    schedule(runner, Query.PROBE, 0);
                });
    }

    /**
     * 接收 trajectory/snapshot 响应。
     *
     * @param topic MQTT 主题
     * @param json MQTT 消息载荷
     */
    public synchronized void handleSnapshot(String topic, String json) {
        try {
            String robotId = robotIdFromSnapshotTopic(topic);
            Map<String, Object> report = objectMapper.readValue(json, MAP_TYPE);
            String commandId = string(report.get("commandId"));
            Pending pending = pendingByCommand.get(commandId);
            if (pending == null || !pending.target.robotId().equals(robotId)) {
                log.debug("轨迹响应已忽略 协议=mqtt 方向=入站 阶段=匹配 结果=丢弃 业务类型=轨迹 机器人标识={} 命令标识={} 原因码=命令标识不匹配",
                        robotId, commandId);
                return;
            }
            Runner runner = runners.get(pending.target);
            if (runner == null || runner.version != pending.version || !commandId.equals(runner.pendingCommandId)
                    || !Objects.equals(positiveLong(report.get("taskInstanceId")), pending.taskInstanceId)
                    || !pending.format.equals(string(report.get("format")))) {
                log.debug("轨迹响应已忽略 协议=mqtt 方向=入站 阶段=匹配 结果=丢弃 业务类型=轨迹 机器人标识={} 命令标识={} 工作流实例标识={} 原因码=响应过期或任务实例不匹配",
                        robotId, commandId, pending.target.workflowInstanceId());
                return;
            }
            pendingByCommand.remove(commandId);
            runner.pendingCommandId = null;
            String status = string(report.get("status")).toLowerCase();
            log.debug("轨迹响应已接受 协议=mqtt 方向=入站 阶段=响应 结果=已接受 业务类型=轨迹 机器人标识={} 命令标识={} 工作流实例标识={} 查询类型={} 状态={}",
                    robotId, commandId, pending.target.workflowInstanceId(), pending.query, status);
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

    /**
     * 接收轨迹历史分段并恢复指定观看者；依据分页标志继续补拉，完成后切换摘要轮询。
     */
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
        if (total != null) {
            runner.totalPoints = total;
        }
        if (start != null) {
            runner.startTime = start;
        }
        if (currentPose != null) {
            runner.currentPose = currentPose;
        }
    }

    private void restartRestore(Runner runner) {
        prepareRestore(runner, watchingSessionIds(runner.target), null);
        schedule(runner, Query.RESTORE, RETRY_MILLIS);
    }

    private void retryFull(Runner runner, Query query) {
        if (query == Query.RESTORE) {
            resetRestore(runner);
        }
        schedule(runner, query, RETRY_MILLIS);
    }

    private void retry(Runner runner, Query query) {
        if (query == Query.RESTORE) {
            resetRestore(runner);
        }
        schedule(runner, query, RETRY_MILLIS);
    }

    private void stop(Runner runner) {
        if (runner.stopped) {
            return;
        }
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
                log.warn("轨迹订阅冲突（TRAJECTORY_WATCH_CONFLICT）：同一机器人存在不同执行轮次，机器人标识={} 目标集合={}", robotId, targets);
            }
        });
        new ArrayList<>(runners.entrySet()).forEach(entry -> {
            if (!desired.contains(entry.getKey())) {
                invalidate(entry.getValue());
                runners.remove(entry.getKey());
            }
        });
        desired.forEach(target -> {
            if (runners.containsKey(target)) {
                return;
            }
            Runner runner = new Runner(target);
            runner.candidateTaskId = taskIdsByRobot.get(target.robotId());
            runners.put(target, runner);
            if (runner.candidateTaskId != null) {
                schedule(runner, Query.PROBE, 0);
            }
        });
    }

    private void schedule(Runner runner, Query query, long delayMillis) {
        if (runner.stopped || runners.get(runner.target) != runner) {
            return;
        }
        if (runner.scheduled != null) {
            runner.scheduled.cancel(false);
        }
        long version = runner.version;
        runner.scheduled = scheduler.schedule(() -> execute(runner.target, version, query),
                Instant.now().plusMillis(delayMillis));
    }

    /**
     * 核对目标代次及在途命令后发起查询，登记响应关联和超时回调，防止同目标并发查询。
     */
    private synchronized void execute(Target target, long version, Query query) {
        Runner runner = runners.get(target);
        if (runner == null || runner.version != version || runner.stopped || runner.pendingCommandId != null) {
            return;
        }
        Long taskInstanceId = query == Query.PROBE ? runner.candidateTaskId : runner.boundTaskId;
        if (taskInstanceId == null || Objects.equals(taskInstanceId, runner.rejectedTaskId)) {
            return;
        }
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
            log.debug("轨迹查询已发布 协议=mqtt 方向=出站 阶段=请求 结果=已发布 业务类型=轨迹 机器人标识={} 工作流实例标识={} 任务实例标识={} 命令标识={} 查询类型={} 格式={}",
                    target.robotId(), target.workflowInstanceId(), taskInstanceId, commandId, query, format);
        } catch (RuntimeException exception) {
            pendingByCommand.remove(commandId);
            runner.pendingCommandId = null;
            log.warn("发布轨迹查询失败，机器人标识={} 工作流实例标识={}", target.robotId(), target.workflowInstanceId(), exception);
            retry(runner, query);
            return;
        }
        scheduler.schedule(() -> timeout(commandId), Instant.now().plusMillis(REQUEST_TIMEOUT_MILLIS));
    }

    private synchronized void timeout(String commandId) {
        Pending pending = pendingByCommand.remove(commandId);
        if (pending == null) {
            return;
        }
        Runner runner = runners.get(pending.target);
        if (runner == null || runner.version != pending.version || !commandId.equals(runner.pendingCommandId)) {
            return;
        }
        runner.pendingCommandId = null;
        log.warn("轨迹查询响应超时 协议=mqtt 阶段=响应 结果=超时 业务类型=轨迹 机器人标识={} 工作流实例标识={} 任务实例标识={} 命令标识={} 查询类型={} 原因码=MQTT响应超时",
                pending.target.robotId(), pending.target.workflowInstanceId(), pending.taskInstanceId,
                commandId, pending.query);
        retry(runner, pending.query);
    }

    private void invalidate(Runner runner) {
        runner.version++;
        if (runner.scheduled != null) {
            runner.scheduled.cancel(false);
        }
        if (runner.pendingCommandId != null) {
            pendingByCommand.remove(runner.pendingCommandId);
        }
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
        if (runner == null || runner.boundTaskId == null || runner.stopped) {
            return;
        }
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
            if (watch.targets.contains(target) && watch.session.isOpen()) {
                sessionIds.add(sessionId);
            }
        });
        return sessionIds;
    }

    /**
     * 按订阅版本恢复轨迹；核对待处理查询和当前目标，分段下发已有数据，防止迟到响应覆盖更新订阅。
     */
    private void emitRestore(Runner runner, List<Map<String, Object>> points,
            Map<String, Object> currentPose, boolean hasMore) {
        TextMessage resetMessage = null;
        TextMessage restoreAppendMessage = null;
        TextMessage deltaAppendMessage = null;
        List<Map<String, Object>> added = null;
        for (Watch watch : watches.values()) {
            String sessionId = watch.session.getId();
            if (!watch.targets.contains(runner.target) || !watch.session.isOpen()) {
                continue;
            }
            if (runner.restoreSessions.contains(sessionId)) {
                if (runner.resetPendingSessions.remove(sessionId)) {
                    if (resetMessage == null) {
                        resetMessage = message(runner, "RESET", points, currentPose);
                    }
                    if (resetMessage != null) {
                        send(watch.session, resetMessage);
                    }
                } else {
                    if (restoreAppendMessage == null) {
                        restoreAppendMessage = message(runner, "APPEND", points, currentPose);
                    }
                    if (restoreAppendMessage != null) {
                        send(watch.session, restoreAppendMessage);
                    }
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
                if (deltaAppendMessage != null) {
                    send(watch.session, deltaAppendMessage);
                }
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
        if (message == null) {
            return;
        }
        for (Watch watch : watches.values()) {
            if (!watch.targets.contains(runner.target) || !watch.session.isOpen()) {
                continue;
            }
            send(watch.session, message);
        }
        int pointCount = points == null ? 0 : points.size();
        if ("APPEND".equals(action)) {
            log.debug("轨迹事件已尝试投递 协议=websocket 方向=出站 阶段=投递 结果=已尝试 业务类型=轨迹 机器人标识={} 工作流实例标识={} 动作={} 点位数={} 目标会话数={}",
                    runner.target.robotId(), runner.target.workflowInstanceId(), action, pointCount,
                    watchingSessionIds(runner.target).size());
        } else {
            log.info("轨迹事件已尝试投递 协议=websocket 方向=出站 阶段=投递 结果=已尝试 业务类型=轨迹 机器人标识={} 工作流实例标识={} 动作={} 点位数={} 目标会话数={}",
                    runner.target.robotId(), runner.target.workflowInstanceId(), action, pointCount,
                    watchingSessionIds(runner.target).size());
        }
    }

    private TextMessage message(Runner runner, String action,
            Collection<Map<String, Object>> points, Map<String, Object> currentPose) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("robotId", runner.target.robotId());
        data.put("workflowInstanceId", runner.target.workflowInstanceId());
        data.put("action", action);
        if (points != null) {
            data.put("points", points);
        }
        if (currentPose != null) {
            data.put("currentPose", currentPose);
        }
        Map<String, Object> event = Map.of(
                "event", "robot.trajectory.changed",
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "data", data);
        try {
            return new TextMessage(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            log.warn("序列化轨迹事件失败，机器人标识={}", runner.target.robotId(), exception);
            return null;
        }
    }

    private void send(WebSocketSession session, TextMessage message) {
        try {
            webSocketPublisher.send(session, message);
        } catch (IOException | IllegalStateException exception) {
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
        if (!(value instanceof Collection<?> collection) || startTime == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            Map<String, Object> point = map(item);
            Double t = finiteDouble(point.get("t"));
            Map<String, Object> timed = timedPose(point, t == null ? null : startTime + t);
            if (timed != null) {
                result.add(timed);
            }
        }
        return result;
    }

    private Map<String, Object> timedPose(Object value, Double timestamp) {
        Map<String, Object> pose = map(value);
        Double x = finiteDouble(pose.get("x"));
        Double y = finiteDouble(pose.get("y"));
        if (timestamp == null || x == null || y == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", timestamp);
        result.put("x", x);
        result.put("y", y);
        Double yaw = finiteDouble(pose.get("yaw"));
        if (yaw != null) {
            result.put("yaw", yaw);
        }
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

    /** 轨迹同步查询阶段：探测、恢复、摘要以及缺口补拉。 */
    private enum Query { /** 探测当前轨迹版本，决定是否需要同步。 */ PROBE, /** 恢复订阅时拉取已有轨迹。 */ RESTORE, /** 查询轨迹摘要，确定可用段范围。 */ SUMMARY, /** 补拉已识别的轨迹缺段。 */ GAP }

    /**
     * 以机器人和工作流实例共同标识一条轨迹同步目标。
     *
     * @param robotId 机器人 ID
     * @param workflowInstanceId 工作流运行实例 ID
     */
    private record Target(String robotId, String workflowInstanceId) {}
    /**
     * 浏览器会话及其当前订阅的轨迹目标集合。
     *
     * @param session WebSocket 会话
     * @param targets 本连接订阅的轨迹目标集合
     */
    private record Watch(WebSocketSession session, Set<Target> targets) {}
    /**
     * 等待响应的轨迹查询上下文，保留目标、版本、格式及查询阶段。
     *
     * @param target 当前需要跟踪的机器人及工作流实例
     * @param version 当前快照或请求版本，用于识别更新先后
     * @param taskInstanceId 设备任务实例 ID
     * @param format 内容或导出文件格式
     * @param query 轨迹查询类型，区分探测、恢复、摘要及缺段补拉
     */
    private record Pending(Target target, long version, Long taskInstanceId, String format, Query query) {}

    /** 单条轨迹目标的同步运行状态，管理版本、恢复、超时和计划任务。 */
    private static final class Runner {
        /**
         * 本同步器负责的机器人及工作流目标。
         */
        private final Target target;
        /**
         * 同步代次，任务切换时递增，旧响应和旧调度任务不得继续更新。
         */
        private long version;
        /**
         * 正在探测、尚未确认绑定的设备任务实例 ID。
         */
        private Long candidateTaskId;
        /**
         * 已探测且不适合绑定的设备任务实例 ID，避免重复探测。
         */
        private Long rejectedTaskId;
        /**
         * 当前已确认绑定的设备任务实例 ID。
         */
        private Long boundTaskId;
        /**
         * 轨迹起始 Unix 时间，单位秒，用于还原相对点时间。
         */
        private Double startTime;
        /**
         * 最近摘要报告的轨迹总点数。
         */
        private Long totalPoints;
        /**
         * 已处理轨迹点的最新绝对时间，单位秒。
         */
        private Double lastTimestamp;
        /**
         * 最近摘要中的当前位置，已补充绝对时间。
         */
        private Map<String, Object> currentPose;
        /**
         * 本轮恢复的时间边界，单位秒，用于区分历史恢复与实时追加。
         */
        private Double restoreCutoff;
        /**
         * 当前仍等待历史轨迹恢复的浏览器连接 ID。
         */
        private final Set<String> restoreSessions = new HashSet<>();
        /**
         * 恢复追加前仍需发送重置事件的连接 ID。
         */
        private final Set<String> resetPendingSessions = new HashSet<>();
        /**
         * 本轮恢复是否已经发送重置事件。
         */
        private boolean resetSent;
        /**
         * 当前目标是否已停止同步，停止后不再发起查询。
         */
        private boolean stopped;
        /**
         * 当前等待响应的查询命令 ID，无在途请求时为空。
         */
        private String pendingCommandId;
        /**
         * 下一次同步任务句柄，目标失效或重新调度时取消。
         */
        private ScheduledFuture<?> scheduled;

        private Runner(Target target) {
            this.target = target;
        }
    }
}
