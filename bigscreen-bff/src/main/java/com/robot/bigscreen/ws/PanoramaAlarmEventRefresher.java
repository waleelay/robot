package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 查询普通告警变化及可处置工作流告警快照，并在人工任务未就绪时短时收敛。 */
@Component
public class PanoramaAlarmEventRefresher {

    private static final Logger log = LoggerFactory.getLogger(PanoramaAlarmEventRefresher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long[] RETRY_DELAYS_MILLIS = {300, 600, 1200, 2400};
    private static final long CONVERGENCE_TIMEOUT_MILLIS = 5000;
    private static final int MAX_WORKFLOW_REQUESTS_PER_IDENTITY = 2;
    private static final int MAX_SEEN_EVENTS_PER_IDENTITY = 128;

    private final PanoramaService panoramaService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final TaskExecutor taskExecutor;
    /**
     * 按调用方传入的授权身份键保存告警刷新状态，最后一个同身份连接退出时移除。
     */
    private final Map<String, RefreshState> states = new ConcurrentHashMap<>();

    /**
     * 初始化 PanoramaAlarmEventRefresher，保存所需依赖及初始运行状态。
     *
     * @param panoramaService 聚合下游设备、任务、地图和告警，维护请求缓存与数据质量标识。
     * @param objectMapper JSON 编解码器
     * @param taskScheduler 后台任务调度器
     * @param taskExecutor 后台任务执行器
     */
    public PanoramaAlarmEventRefresher(
            PanoramaService panoramaService,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.panoramaService = panoramaService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 为单个目标连接请求当前告警快照，避免新标签页使同身份其他会话重复消费。
     *
     * @param sessionId 会话 ID
     * @param authentication 经过认证的当前用户上下文
     * @param publisher 向目标浏览器或身份分组投递消息的回调
     */
    public void requestSnapshot(String sessionId, Authentication authentication, Predicate<String> publisher) {
        RefreshState state = state(sessionId);
        synchronized (state) {
            state.eventKey = null;
            state.authentication = authentication;
            state.workflowSnapshotPublishers.add(publisher);
            state.workflowRevision++;
            state.workflowFailureRetryDeadlineMillis =
                    System.currentTimeMillis() + CONVERGENCE_TIMEOUT_MILLIS;
            state.workflowRetryCount = 0;
            state.workflowDirty = true;
            scheduleWorkflowIfNeeded(sessionId, state, 0);
        }
    }

    /**
     * 合并同身份告警失效通知并触发权威补查；按现有窗口等待数据收敛。
     *
     * @param sessionId 会话 ID
     * @param authentication 经过认证的当前用户上下文
     * @param publisher 向目标浏览器或身份分组投递消息的回调
     */
    public void requestRefresh(String sessionId, Authentication authentication, Predicate<String> publisher) {
        requestRefresh(sessionId, authentication, publisher, null);
    }

    /**
     * 合并同身份告警失效通知并触发权威补查；按现有窗口等待数据收敛。
     *
     * @param sessionId 会话 ID
     * @param authentication 经过认证的当前用户上下文
     * @param publisher 向目标浏览器或身份分组投递消息的回调
     * @param eventKey 事件去重或合并使用的标识
     */
    public void requestRefresh(
            String sessionId, Authentication authentication, Predicate<String> publisher, String eventKey) {
        RefreshState state = state(sessionId);
        synchronized (state) {
            if (eventKey != null && !state.seenEvents.add(eventKey)) {
                log.debug("重复告警事件已忽略 协议=websocket 阶段=去重 结果=丢弃 业务类型=告警 身份={} 事件键={} 原因码=重复事件",
                        sessionId, eventKey);
                return;
            }
            if (state.seenEvents.size() > MAX_SEEN_EVENTS_PER_IDENTITY) {
                state.seenEvents.remove(state.seenEvents.iterator().next());
            }
            state.authentication = authentication;
            state.publisher = publisher;
            state.eventKey = eventKey;
            state.workflowRevision++;
            state.retryDeadlineMillis = System.currentTimeMillis() + CONVERGENCE_TIMEOUT_MILLIS;
            state.workflowFailureRetryDeadlineMillis = state.retryDeadlineMillis;
            state.workflowRetryCount = 0;
            state.workflowDirty = true;
            scheduleWorkflowIfNeeded(sessionId, state, 0);
        }
        state.alarmsDirty.set(true);
        scheduleAlarmsIfNeeded(sessionId, state);
    }

    private RefreshState state(String sessionId) {
        return states.computeIfAbsent(sessionId, ignored -> new RefreshState());
    }

    /**
     * 移除该身份的告警刷新状态，后续过期结果不得继续发布。
     *
     * @param sessionId 会话 ID
     */
    public void remove(String sessionId) {
        RefreshState state = states.remove(sessionId);
        if (state != null) {
            synchronized (state) {
                if (state.workflowPending != null) {
                    state.workflowPending.cancel(false);
                }
                state.workflowScheduledAt = null;
            }
        }
    }

    // 新事件可取消并提前旧退避任务；调用方持有 state 锁。
    private void scheduleWorkflowIfNeeded(String sessionId, RefreshState state, long delayMillis) {
        Instant due = Instant.now().plusMillis(delayMillis);
        if (state.workflowRunning >= MAX_WORKFLOW_REQUESTS_PER_IDENTITY
                || (state.workflowScheduledAt != null && !due.isBefore(state.workflowScheduledAt))) {
            return;
        }
        if (state.workflowPending != null) {
            state.workflowPending.cancel(false);
        }
        state.workflowScheduledAt = due;
        Runnable dispatch = () -> taskExecutor.execute(() -> refreshWorkflow(sessionId, state, due));
        if (delayMillis == 0) {
            dispatch.run();
        } else {
            state.workflowPending = taskScheduler.schedule(dispatch, due);
        }
    }

    /**
     * 按身份及通知代次补查可处置工作流告警；有限次数退避等待收敛，旧代次迟到结果不覆盖新快照。
     */
    private void refreshWorkflow(String sessionId, RefreshState state, Instant due) {
        long revision;
        Authentication authentication;
        synchronized (state) {
            if (states.get(sessionId) != state || !due.equals(state.workflowScheduledAt)) {
                return;
            }
            state.workflowScheduledAt = null;
            state.workflowPending = null;
            state.workflowRunning++;
            state.workflowDirty = false;
            revision = state.workflowRevision;
            authentication = state.authentication;
        }
        boolean retry = false;
        try {
            List<Map<String, Object>> workflowItems = maps(withAuthentication(
                    authentication, panoramaService::actionableWorkflowAlarms).get("items"));
            Map<String, Map<String, Object>> currentWorkflowAlarms;
            List<Predicate<String>> snapshotPublishers;
            Predicate<String> publisher;
            boolean changed;
            boolean snapshotPublished;
            synchronized (state) {
                if (states.get(sessionId) != state || revision != state.workflowRevision) {
                    return;
                }
                currentWorkflowAlarms = index(workflowItems);
                snapshotPublished = state.workflowSnapshotPublished;
                changed = !snapshotPublished
                        || !Objects.equals(state.previousWorkflowAlarms, currentWorkflowAlarms);
                snapshotPublishers = List.copyOf(state.workflowSnapshotPublishers);
                state.workflowSnapshotPublishers.clear();
                publisher = state.publisher;
            }
            String event = null;
            if (!snapshotPublishers.isEmpty() || changed) {
                event = workflowSnapshotEvent(workflowItems, state.eventKey);
            }
            boolean snapshotDelivered = false;
            for (Predicate<String> snapshotPublisher : snapshotPublishers) {
                snapshotDelivered |= snapshotPublisher.test(event);
            }
            boolean delivered = snapshotDelivered;
            boolean initialSnapshotDelivered = !snapshotPublished && snapshotDelivered;
            if (publisher != null && changed && !initialSnapshotDelivered) {
                delivered |= publisher.test(event);
            }
            log.info("工作流告警刷新完成 协议=websocket 阶段=刷新 结果={} 业务类型=工作流告警 身份={} 事件键={} 条目数={} 是否变化={} 是否已投递={} 修订号={}",
                    changed ? (delivered ? "已投递" : "投递失败") : "无变化",
                    sessionId, state.eventKey, currentWorkflowAlarms.size(), changed, delivered, revision);
            synchronized (state) {
                if (states.get(sessionId) != state || revision != state.workflowRevision) {
                    return;
                }
                if (!changed || delivered) {
                    state.previousWorkflowAlarms = currentWorkflowAlarms;
                    state.workflowSnapshotPublished = true;
                }
                long deadline = changed && !delivered
                        ? state.workflowFailureRetryDeadlineMillis
                        : state.retryDeadlineMillis;
                retry = (!changed
                        || (!snapshotPublished && currentWorkflowAlarms.isEmpty())
                        || (changed && !delivered))
                        && System.currentTimeMillis() < deadline;
            }
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图工作流告警失败，身份={}", sessionId, exception);
            retry = revision == state.workflowRevision
                    && System.currentTimeMillis() < state.workflowFailureRetryDeadlineMillis;
        } finally {
            synchronized (state) {
                long delay = 0;
                if (!state.workflowDirty && retry
                        && state.workflowRetryCount < RETRY_DELAYS_MILLIS.length) {
                    state.workflowDirty = true;
                    delay = jitteredDelay(RETRY_DELAYS_MILLIS[state.workflowRetryCount++]);
                    log.info("工作流告警刷新已安排重试 阶段=重试 结果=已安排 业务类型=工作流告警 身份={} 事件键={} 原因码=等待数据收敛 尝试次数={} 延迟毫秒={}",
                            sessionId, state.eventKey, state.workflowRetryCount, delay);
                }
                state.workflowRunning--;
                if (states.get(sessionId) == state && state.workflowDirty) {
                    scheduleWorkflowIfNeeded(sessionId, state, delay);
                } else {
                    state.workflowRetryCount = 0;
                }
            }
        }
    }

    private long jitteredDelay(long delayMillis) {
        long spread = Math.max(1L, delayMillis / 5L);
        return delayMillis + ThreadLocalRandom.current().nextLong(-spread, spread + 1L);
    }

    private void scheduleAlarmsIfNeeded(String sessionId, RefreshState state) {
        if (state.alarmsScheduled.compareAndSet(false, true)) {
            taskExecutor.execute(() -> refreshAlarms(sessionId, state));
        }
    }

    private void refreshAlarms(String sessionId, RefreshState state) {
        if (states.get(sessionId) != state) {
            return;
        }
        state.alarmsDirty.set(false);
        try {
            Map<String, Object> alarms = withAuthentication(
                    state.authentication, panoramaService::alarmEventSnapshot);
            if (states.get(sessionId) != state) {
                return;
            }
            boolean changed = !Objects.equals(state.previousAlarms, alarms);
            Predicate<String> publisher = state.publisher;
            boolean delivered = publisher != null && changed
                    && publisher.test(alarmSnapshotEvent(alarms, state.eventKey));
            log.info("普通告警刷新完成 协议=websocket 阶段=刷新 结果={} 业务类型=告警 身份={} 事件键={} 是否变化={} 是否已投递={} 原因码={}",
                    changed ? (delivered ? "已投递" : "投递失败") : "无变化",
                    sessionId, state.eventKey, changed, delivered,
                    changed ? "告警列表快照" : "快照无变化");
            if (!changed || delivered) {
                state.previousAlarms = alarms;
            }
        } catch (RuntimeException exception) {
            log.warn("刷新全景地图普通告警失败，身份={}", sessionId, exception);
        } finally {
            state.alarmsScheduled.set(false);
            if (states.get(sessionId) == state && state.alarmsDirty.get()) {
                scheduleAlarmsIfNeeded(sessionId, state);
            }
        }
    }

    private Map<String, Map<String, Object>> index(List<Map<String, Object>> alarms) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> alarm : alarms) {
            Object alarmId = alarm.get("alarmId");
            if (alarmId != null) {
                result.put(String.valueOf(alarmId), new LinkedHashMap<>(alarm));
            }
        }
        return result;
    }

    private String alarmSnapshotEvent(Map<String, Object> alarms, String correlationId) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "panorama.alarms.changed");
            event.put("timestamp", TIME_FORMATTER.format(LocalDateTime.now()));
            event.put("data", alarms);
            if (correlationId != null) {
                event.put("correlationId", correlationId);
            }
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化全景地图告警快照失败", exception);
        }
    }

    private String workflowSnapshotEvent(List<Map<String, Object>> items, String correlationId) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "panorama.workflow-alarms.changed");
            event.put("timestamp", TIME_FORMATTER.format(LocalDateTime.now()));
            event.put("data", Map.of("total", items.size(), "items", items));
            if (correlationId != null) {
                event.put("correlationId", correlationId);
            }
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化工作流告警快照事件失败", exception);
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
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    /** 合并告警失效通知，记录待执行任务及运行期间的新变化。 */
    private static final class RefreshState {
        /**
         * 是否已有普通告警刷新调度或正在执行。
         */
        private final AtomicBoolean alarmsScheduled = new AtomicBoolean();
        /**
         * 普通告警刷新期间是否又收到失效通知。
         */
        private final AtomicBoolean alarmsDirty = new AtomicBoolean();
        /**
         * 下一次工作流告警刷新任务句柄。
         */
        private ScheduledFuture<?> workflowPending;
        /**
         * 工作流告警任务计划执行时间，用于识别过期调度。
         */
        private Instant workflowScheduledAt;
        /**
         * 当前身份正在执行的工作流告警查询数量。
         */
        private int workflowRunning;
        /**
         * 工作流告警查询期间是否有新通知。
         */
        private boolean workflowDirty;
        /**
         * 工作流告警刷新代次，防止迟到结果覆盖新请求。
         */
        private volatile long workflowRevision;
        /**
         * 等待人工任务和告警数据收敛的重试次数。
         */
        private int workflowRetryCount;
        /**
         * 等待首次工作流告警快照的定向投递回调。
         */
        private final List<Predicate<String>> workflowSnapshotPublishers = new ArrayList<>();
        /**
         * 当前身份已处理的有界事件键集合，按插入顺序淘汰旧键。
         */
        private final LinkedHashSet<String> seenEvents = new LinkedHashSet<>();
        /**
         * 当前身份最近的认证上下文，供异步查询使用。
         */
        private volatile Authentication authentication;
        /**
         * 向当前身份投递告警变化的回调。
         */
        private volatile Predicate<String> publisher;
        /**
         * 上次成功投递的普通告警快照。
         */
        private volatile Map<String, Object> previousAlarms = Map.of();
        /**
         * 按告警 ID 保存上次成功投递的可处置工作流告警。
         */
        private volatile Map<String, Map<String, Object>> previousWorkflowAlarms = Map.of();
        /**
         * 数据收敛重试的系统截止时间，单位毫秒。
         */
        private volatile long retryDeadlineMillis;
        /**
         * 工作流告警查询失败后允许重试的系统截止时间，单位毫秒。
         */
        private volatile long workflowFailureRetryDeadlineMillis;
        /**
         * 是否已成功发布工作流告警快照。
         */
        private volatile boolean workflowSnapshotPublished;
        /**
         * 本轮刷新对应的失效通知关联键。
         */
        private volatile String eventKey;
    }
}
