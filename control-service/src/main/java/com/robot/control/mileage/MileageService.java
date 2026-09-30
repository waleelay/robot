package com.robot.control.mileage;

import com.robot.control.config.DateTimeConfig;
import com.robot.control.ws.MediaWebSocketPublisher;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 计算设备里程增量，并按分钟保存可用于统计的结果。 */
@Service
public class MileageService {

    private static final Logger log = LoggerFactory.getLogger(MileageService.class);
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MileageProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    /**
     * 按机器人累计尚未触发里程事件的有效米数，达到发布阈值后清零。
     */
    private final Map<String, BigDecimal> unpublishedMeters = new ConcurrentHashMap<>();

    /**
     * 初始化 MileageService，保存所需依赖及初始运行状态。
     *
     * @param jdbcTemplate 执行参数化 SQL 的 JDBC 访问器
     * @param transactionManager 数据库事务管理器
     * @param properties 服务配置
     * @param webSocketPublisher 向已连接客户端投递业务事件的组件
     */
    public MileageService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MileageProperties properties,
            MediaWebSocketPublisher webSocketPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS control_device_mileage_checkpoint (
                    robot_id VARCHAR(128) NOT NULL PRIMARY KEY,
                    last_total_mileage_m DECIMAL(18,3) NULL,
                    last_current_mileage_m DECIMAL(18,3) NULL,
                    last_event_time TIMESTAMP(3) NOT NULL,
                    last_message_id VARCHAR(160) NULL,
                    updated_at TIMESTAMP(3) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS control_device_mileage_bucket (
                    robot_id VARCHAR(128) NOT NULL,
                    bucket_time TIMESTAMP(3) NOT NULL,
                    mileage_m DECIMAL(18,3) NOT NULL DEFAULT 0,
                    sample_count BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (robot_id, bucket_time),
                    INDEX idx_mileage_bucket_time (bucket_time),
                    INDEX idx_mileage_bucket_robot_time (robot_id, bucket_time)
                )
                """);
    }

    /**
     * 首次读数只建立基线；重复、乱序、回退和异常跳变不会污染有效里程。
     *
     * @param reading 设备上报并已提取的里程读数
     * @return 读数是否被接受、质量分类及非负里程增量
     */
    public MileageResult record(MileageReading reading) {
        if (!valid(reading)) {
            return MileageResult.ignored("INVALID");
        }
        MileageResult result = transactionTemplate.execute(status -> recordInTransaction(reading));
        if (result == null) {
            return MileageResult.ignored("TRANSACTION_EMPTY");
        }
        if (List.of("RESET", "ESTIMATED", "SUSPECT").contains(result.quality())) {
            log.warn("检测到里程质量异常，机器人标识={} 质量={} 事件时间={} 增量米数={}",
                    reading.robotId(), result.quality(), DateTimeConfig.format(reading.eventTime()), result.deltaMeters());
        }
        publishIfNeeded(reading, result);
        return result;
    }

    /**
     * 查询分钟桶中的有效里程。
     *
     * @param startTime 上海时区区间起点，包含该时刻
     * @param endTime 上海时区区间终点，包含该时刻
     * @param robotIds 机器人标识集合
     * @return 统计窗口内的有效里程、样本数及按设备分组结果
     */
    public MileageSummaryResponse summary(
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<String> robotIds) {
        List<String> normalizedRobotIds = robotIds == null
                ? List.of()
                : robotIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        String filter = normalizedRobotIds.isEmpty()
                ? ""
                : " AND robot_id IN (" + String.join(",", java.util.Collections.nCopies(
                        normalizedRobotIds.size(), "?")) + ")";
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.valueOf(startTime));
        args.add(Timestamp.valueOf(endTime));
        args.addAll(normalizedRobotIds);

        List<RobotMileage> rows = jdbcTemplate.query(
                "SELECT robot_id, SUM(mileage_m) AS mileage_m, SUM(sample_count) AS sample_count "
                        + "FROM control_device_mileage_bucket "
                        + "WHERE bucket_time >= ? AND bucket_time <= ?" + filter
                        + " GROUP BY robot_id ORDER BY robot_id",
                (resultSet, rowNum) -> new RobotMileage(
                        resultSet.getString("robot_id"),
                        resultSet.getBigDecimal("mileage_m"),
                        resultSet.getLong("sample_count")),
                args.toArray());

        Map<String, RobotMileage> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.robotId(), row));
        List<String> resultRobotIds = normalizedRobotIds.isEmpty()
                ? rows.stream().map(RobotMileage::robotId).toList()
                : normalizedRobotIds;
        List<MileageSummaryResponse.RobotMileageSummary> byRobot = new ArrayList<>();
        BigDecimal total = ZERO;
        long sampleCount = 0;
        for (String robotId : resultRobotIds) {
            RobotMileage row = indexed.get(robotId);
            boolean hasData = row != null && row.sampleCount() > 0;
            byRobot.add(new MileageSummaryResponse.RobotMileageSummary(
                    robotId, hasData, hasData ? scale(row.mileageMeters()) : null,
                    hasData ? row.sampleCount() : 0L));
            if (hasData) {
                total = total.add(row.mileageMeters());
                sampleCount += row.sampleCount();
            }
        }

        return new MileageSummaryResponse(
                DateTimeConfig.format(startTime), DateTimeConfig.format(endTime), CHINA_ZONE.getId(),
                sampleCount > 0, sampleCount > 0 ? scale(total) : null, sampleCount, "m", byRobot);
    }

    /**
     * 在设备检查点行锁内判定重复与乱序读数，更新基线，并仅将有效增量写入分钟桶。
     */
    private MileageResult recordInTransaction(MileageReading reading) {
        Checkpoint checkpoint = checkpoint(reading.robotId());
        LocalDateTime eventTime = reading.eventTime().atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        BigDecimal total = meters(reading.totalMileageMeters());
        BigDecimal current = meters(reading.currentMileageMeters());
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        if (checkpoint == null) {
            jdbcTemplate.update("""
                            INSERT INTO control_device_mileage_checkpoint
                            (robot_id, last_total_mileage_m, last_current_mileage_m, last_event_time,
                             last_message_id, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    reading.robotId(), total, current, Timestamp.valueOf(eventTime), reading.messageId(),
                    Timestamp.valueOf(now));
            return MileageResult.ignored("BASELINE");
        }
        if (duplicateOrOutOfOrder(reading, checkpoint, eventTime)) {
            return MileageResult.ignored("DUPLICATE_OR_OUT_OF_ORDER");
        }

        MileageResult result = calculate(checkpoint, total, current, eventTime);
        jdbcTemplate.update("""
                        UPDATE control_device_mileage_checkpoint
                        SET last_total_mileage_m = ?, last_current_mileage_m = ?, last_event_time = ?,
                            last_message_id = ?, updated_at = ?
                        WHERE robot_id = ?
                        """,
                total != null ? total : checkpoint.lastTotalMeters(),
                current != null ? current : checkpoint.lastCurrentMeters(),
                Timestamp.valueOf(eventTime), reading.messageId(), Timestamp.valueOf(now), reading.robotId());
        if (included(result)) {
            saveBucket(reading, eventTime, result, now);
        }
        return result;
    }

    /**
     * 优先使用总累计里程计算增量；回退标记 RESET，超过时间和速度上限的跳变标记异常而不计入有效里程。
     */
    private MileageResult calculate(
            Checkpoint checkpoint,
            BigDecimal total,
            BigDecimal current,
            LocalDateTime eventTime) {
        BigDecimal delta = ZERO;
        String quality = "NORMAL";
        if (total != null && checkpoint.lastTotalMeters() != null) {
            delta = total.subtract(checkpoint.lastTotalMeters());
            if (delta.signum() < 0) {
                return new MileageResult(ZERO, "RESET", true);
            }
        } else if (current != null && checkpoint.lastCurrentMeters() != null) {
            delta = current.subtract(checkpoint.lastCurrentMeters());
            if (delta.signum() < 0) {
                delta = current;
                quality = "ESTIMATED";
            }
        }
        long elapsedMillis = Duration.between(checkpoint.lastEventTime(), eventTime).toMillis();
        if (delta.signum() > 0 && elapsedMillis > 0
                && delta.doubleValue() * 1000.0 / elapsedMillis > properties.getMaxSpeedMps()) {
            quality = "SUSPECT";
        }
        return new MileageResult(scale(delta.max(BigDecimal.ZERO)), quality, true);
    }

    private void saveBucket(
            MileageReading reading,
            LocalDateTime eventTime,
            MileageResult result,
            LocalDateTime now) {
        jdbcTemplate.update("""
                        INSERT INTO control_device_mileage_bucket
                        (robot_id, bucket_time, mileage_m, sample_count, updated_at)
                        VALUES (?, ?, ?, 1, ?)
                        ON DUPLICATE KEY UPDATE
                          mileage_m = mileage_m + VALUES(mileage_m),
                          sample_count = sample_count + 1,
                          updated_at = VALUES(updated_at)
                        """,
                reading.robotId(), Timestamp.valueOf(eventTime.truncatedTo(ChronoUnit.MINUTES)),
                result.deltaMeters(), Timestamp.valueOf(now));
    }

    private Checkpoint checkpoint(String robotId) {
        List<Checkpoint> rows = jdbcTemplate.query("""
                        SELECT last_total_mileage_m, last_current_mileage_m, last_event_time, last_message_id
                        FROM control_device_mileage_checkpoint
                        WHERE robot_id = ? FOR UPDATE
                        """,
                (resultSet, rowNum) -> new Checkpoint(
                        resultSet.getBigDecimal("last_total_mileage_m"),
                        resultSet.getBigDecimal("last_current_mileage_m"),
                        resultSet.getTimestamp("last_event_time").toLocalDateTime(),
                        resultSet.getString("last_message_id")),
                robotId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean duplicateOrOutOfOrder(
            MileageReading reading,
            Checkpoint checkpoint,
            LocalDateTime eventTime) {
        return (reading.messageId() != null
                        && !reading.messageId().isBlank()
                        && reading.messageId().equals(checkpoint.lastMessageId()))
                || !eventTime.isAfter(checkpoint.lastEventTime());
    }

    private boolean included(MileageResult result) {
        return result.deltaMeters().signum() > 0
                && !"RESET".equals(result.quality())
                && !"SUSPECT".equals(result.quality());
    }

    private void publishIfNeeded(MileageReading reading, MileageResult result) {
        if (!included(result)) {
            return;
        }
        BigDecimal accumulated = unpublishedMeters.merge(reading.robotId(), result.deltaMeters(), BigDecimal::add);
        BigDecimal threshold = BigDecimal.valueOf(Math.max(0.1, properties.getPublishDistanceThresholdMeters()));
        if (accumulated.compareTo(threshold) < 0) {
            return;
        }
        unpublishedMeters.put(reading.robotId(), ZERO);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("robotId", reading.robotId());
        event.put("deltaMeters", scale(accumulated));
        event.put("totalMileage", reading.totalMileageMeters());
        event.put("currentMileage", reading.currentMileageMeters());
        event.put("updatedAt", DateTimeConfig.format(reading.eventTime()));
        webSocketPublisher.publish("robot.mileage.changed", event);
    }

    private boolean valid(MileageReading reading) {
        return reading != null
                && reading.robotId() != null
                && !reading.robotId().isBlank()
                && reading.eventTime() != null
                && (nonNegative(reading.totalMileageMeters()) || nonNegative(reading.currentMileageMeters()));
    }

    private boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private BigDecimal meters(BigDecimal value) {
        return value == null || value.signum() < 0 ? null : scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 已持久化的设备读数，用于识别重复、乱序和累计里程回退；事件时间为上海本地时间。
     *
     * @param lastTotalMeters 上次接受的总累计里程，单位米
     * @param lastCurrentMeters 上次接受的本轮累计里程，单位米
     * @param lastEventTime 上次接受的里程读数时间
     * @param lastMessageId 上次接受的消息 ID，用于去重
     */
    private record Checkpoint(
            BigDecimal lastTotalMeters,
            BigDecimal lastCurrentMeters,
            LocalDateTime lastEventTime,
            String lastMessageId) {
    }

    /**
     * 按设备汇总的分钟桶查询结果；是否有数据由有效样本数判断，不能仅看米数是否为零。
     *
     * @param robotId 机器人 ID
     * @param mileageMeters 里程米数，无数据时为 null
     * @param sampleCount 有效样本总数
     */
    private record RobotMileage(
            String robotId,
            BigDecimal mileageMeters,
            long sampleCount) {
    }

    /**
     * 单次读数处理结果，增量单位为米。
     *
     * @param deltaMeters 本次计算的非负增量；是否写入统计桶仍由质量状态判断
     * @param quality 读数质量或忽略原因，不是里程查询响应字段
     * @param accepted 是否处理了更新的有效读数；为 true 不代表一定计入统计或发布事件
     */
    public record MileageResult(BigDecimal deltaMeters, String quality, boolean accepted) {
        static MileageResult ignored(String quality) {
            return new MileageResult(ZERO, quality, false);
        }
    }
}
