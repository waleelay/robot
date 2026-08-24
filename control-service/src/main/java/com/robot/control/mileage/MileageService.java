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
import org.springframework.jdbc.BadSqlGrammarException;
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
    private final Map<String, BigDecimal> unpublishedMeters = new ConcurrentHashMap<>();

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
                    quality VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                    quality_known BOOLEAN NOT NULL DEFAULT FALSE,
                    updated_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (robot_id, bucket_time),
                    INDEX idx_mileage_bucket_time (bucket_time),
                    INDEX idx_mileage_bucket_robot_time (robot_id, bucket_time)
                )
                """);
        addQualityKnownColumnForExistingSchema();
    }

    /** 旧分钟桶没有可靠质量证据，新增列默认保持 false；新记录写入时明确置为 true。 */
    private void addQualityKnownColumnForExistingSchema() {
        try {
            jdbcTemplate.queryForList("SELECT quality_known FROM control_device_mileage_bucket WHERE 1 = 0");
        } catch (BadSqlGrammarException exception) {
            jdbcTemplate.execute("""
                    ALTER TABLE control_device_mileage_bucket
                    ADD COLUMN quality_known BOOLEAN NOT NULL DEFAULT FALSE
                    """);
        }
    }

    /** 首次读数只建立基线；重复、乱序、回退和异常跳变不会污染有效里程。 */
    public MileageResult record(MileageReading reading) {
        if (!valid(reading)) {
            return MileageResult.ignored("INVALID");
        }
        MileageResult result = transactionTemplate.execute(status -> recordInTransaction(reading));
        if (result == null) {
            return MileageResult.ignored("TRANSACTION_EMPTY");
        }
        if (List.of("RESET", "ESTIMATED", "SUSPECT").contains(result.quality())) {
            log.warn("检测到里程质量异常，机器人ID={} 质量={} 事件时间={} 增量米数={}",
                    reading.robotId(), result.quality(), DateTimeConfig.format(reading.eventTime()), result.deltaMeters());
        }
        publishIfNeeded(reading, result);
        return result;
    }

    /** 查询分钟桶中的有效里程；历史桶质量未知时保留总量并返回 UNKNOWN。 */
    public Map<String, Object> summary(
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
                "SELECT robot_id, SUM(mileage_m) AS mileage_m, SUM(sample_count) AS sample_count, "
                        + "MAX(CASE WHEN quality = 'ESTIMATED' THEN 1 ELSE 0 END) AS has_estimated, "
                        + "MIN(CASE WHEN quality_known THEN 1 ELSE 0 END) AS all_quality_known "
                        + "FROM control_device_mileage_bucket "
                        + "WHERE bucket_time >= ? AND bucket_time <= ?" + filter
                        + " GROUP BY robot_id ORDER BY robot_id",
                (resultSet, rowNum) -> new RobotMileage(
                        resultSet.getString("robot_id"),
                        resultSet.getBigDecimal("mileage_m"),
                        resultSet.getLong("sample_count"),
                        resultSet.getInt("has_estimated") == 1,
                        resultSet.getInt("all_quality_known") == 1),
                args.toArray());

        Map<String, RobotMileage> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.robotId(), row));
        List<String> resultRobotIds = normalizedRobotIds.isEmpty()
                ? rows.stream().map(RobotMileage::robotId).toList()
                : normalizedRobotIds;
        List<Map<String, Object>> byRobot = new ArrayList<>();
        BigDecimal total = ZERO;
        long sampleCount = 0;
        boolean allQualityKnown = true;
        boolean hasEstimated = false;
        for (String robotId : resultRobotIds) {
            RobotMileage row = indexed.get(robotId);
            boolean hasData = row != null && row.sampleCount() > 0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("robotId", robotId);
            item.put("hasData", hasData);
            item.put("mileageMeters", hasData ? scale(row.mileageMeters()) : null);
            item.put("sampleCount", hasData ? row.sampleCount() : 0L);
            item.put("quality", hasData ? quality(row.qualityKnown(), row.hasEstimated()) : "NO_DATA");
            byRobot.add(item);
            if (hasData) {
                total = total.add(row.mileageMeters());
                sampleCount += row.sampleCount();
                allQualityKnown &= row.qualityKnown();
                hasEstimated |= row.hasEstimated();
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("startTime", DateTimeConfig.format(startTime));
        response.put("endTime", DateTimeConfig.format(endTime));
        response.put("timezone", CHINA_ZONE.getId());
        response.put("hasData", sampleCount > 0);
        response.put("totalMeters", sampleCount > 0 ? scale(total) : null);
        response.put("sampleCount", sampleCount);
        response.put("quality", sampleCount == 0 ? "NO_DATA" : quality(allQualityKnown, hasEstimated));
        response.put("unit", "m");
        response.put("byRobot", byRobot);
        return response;
    }

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
                        (robot_id, bucket_time, mileage_m, sample_count, quality, quality_known, updated_at)
                        VALUES (?, ?, ?, 1, ?, TRUE, ?)
                        ON DUPLICATE KEY UPDATE
                          mileage_m = mileage_m + VALUES(mileage_m),
                          sample_count = sample_count + 1,
                          quality = CASE
                            WHEN quality = 'ESTIMATED' OR ? = 'ESTIMATED' THEN 'ESTIMATED'
                            ELSE 'NORMAL'
                          END,
                          updated_at = VALUES(updated_at)
                        """,
                reading.robotId(), Timestamp.valueOf(eventTime.truncatedTo(ChronoUnit.MINUTES)),
                result.deltaMeters(), result.quality(), Timestamp.valueOf(now), result.quality());
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

    private String quality(boolean known, boolean estimated) {
        if (!known) {
            return "UNKNOWN";
        }
        return estimated ? "ESTIMATED" : "NORMAL";
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

    private record Checkpoint(
            BigDecimal lastTotalMeters,
            BigDecimal lastCurrentMeters,
            LocalDateTime lastEventTime,
            String lastMessageId) {
    }

    private record RobotMileage(
            String robotId,
            BigDecimal mileageMeters,
            long sampleCount,
            boolean hasEstimated,
            boolean qualityKnown) {
    }

    public record MileageResult(BigDecimal deltaMeters, String quality, boolean accepted) {
        static MileageResult ignored(String quality) {
            return new MileageResult(ZERO, quality, false);
        }
    }
}
