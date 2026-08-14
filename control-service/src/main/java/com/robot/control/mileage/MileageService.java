package com.robot.control.mileage;

import com.robot.control.config.DateTimeConfig;
import com.robot.control.ws.MediaWebSocketPublisher;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

/** 计算并持久化设备里程增量。 */
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
                    mileage_epoch BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP(3) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS control_device_mileage_bucket (
                    robot_id VARCHAR(128) NOT NULL,
                    bucket_time TIMESTAMP(3) NOT NULL,
                    map_id VARCHAR(128) NOT NULL DEFAULT '',
                    mileage_m DECIMAL(18,3) NOT NULL DEFAULT 0,
                    sample_count BIGINT NOT NULL DEFAULT 0,
                    quality VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                    updated_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (robot_id, bucket_time, map_id),
                    INDEX idx_mileage_bucket_time (bucket_time),
                    INDEX idx_mileage_bucket_robot_time (robot_id, bucket_time)
                )
                """);
    }

    /** 记录一条设备里程读数。首次读数只建立基线。 */
    public MileageResult record(MileageReading reading) {
        if (!valid(reading)) {
            return MileageResult.ignored("INVALID");
        }
        MileageResult result = transactionTemplate.execute(status -> recordInTransaction(reading));
        if (result == null) {
            return MileageResult.ignored("TRANSACTION_EMPTY");
        }
        publishIfNeeded(reading, result);
        return result;
    }

    /** 查询时间范围内的累计移动里程。 */
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
        List<Object> rangeArgs = new ArrayList<>();
        rangeArgs.add(Timestamp.valueOf(startTime));
        rangeArgs.add(Timestamp.valueOf(endTime));
        rangeArgs.addAll(normalizedRobotIds);

        List<Map<String, Object>> byRobot = jdbcTemplate.query(
                "SELECT robot_id, SUM(mileage_m) AS mileage_m "
                        + "FROM control_device_mileage_bucket "
                        + "WHERE bucket_time >= ? AND bucket_time <= ? AND quality = 'NORMAL'"
                        + filter + " GROUP BY robot_id ORDER BY robot_id",
                (resultSet, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("robotId", resultSet.getString("robot_id"));
                    row.put("mileageMeters", resultSet.getBigDecimal("mileage_m"));
                    return row;
                },
                rangeArgs.toArray());
        BigDecimal total = byRobot.stream()
                .map(item -> (BigDecimal) item.get("mileageMeters"))
                .reduce(ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);

        List<Object> checkpointArgs = new ArrayList<>(normalizedRobotIds);
        Long checkpointCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM control_device_mileage_checkpoint WHERE 1=1" + filter,
                Long.class,
                checkpointArgs.toArray());
        return Map.of(
                "startTime", DateTimeConfig.format(startTime),
                "endTime", DateTimeConfig.format(endTime),
                "totalMeters", total,
                "unit", "m",
                "hasData", checkpointCount != null && checkpointCount > 0,
                "byRobot", byRobot);
    }

    private MileageResult recordInTransaction(MileageReading reading) {
        Checkpoint checkpoint = checkpoint(reading.robotId());
        LocalDateTime eventTime = reading.eventTime().atZoneSameInstant(CHINA_ZONE).toLocalDateTime();
        BigDecimal total = meters(reading.totalMileageMeters());
        BigDecimal current = meters(reading.currentMileageMeters());
        if (checkpoint == null) {
            jdbcTemplate.update("""
                            INSERT INTO control_device_mileage_checkpoint
                            (robot_id, last_total_mileage_m, last_current_mileage_m, last_event_time,
                             last_message_id, mileage_epoch, updated_at)
                            VALUES (?, ?, ?, ?, ?, 0, ?)
                            """,
                    reading.robotId(), total, current, Timestamp.valueOf(eventTime), reading.messageId(),
                    Timestamp.valueOf(LocalDateTime.now(CHINA_ZONE)));
            return MileageResult.ignored("BASELINE");
        }
        if ((reading.messageId() != null
                        && !reading.messageId().isBlank()
                        && reading.messageId().equals(checkpoint.lastMessageId()))
                || !eventTime.isAfter(checkpoint.lastEventTime())) {
            return MileageResult.ignored("DUPLICATE_OR_OUT_OF_ORDER");
        }

        long epoch = checkpoint.epoch();
        String quality = "NORMAL";
        BigDecimal delta = ZERO;
        if (total != null && checkpoint.lastTotalMeters() != null) {
            delta = total.subtract(checkpoint.lastTotalMeters());
            if (delta.signum() < 0) {
                delta = ZERO;
                epoch++;
                quality = "RESET";
            }
        } else if (current != null && checkpoint.lastCurrentMeters() != null) {
            delta = current.subtract(checkpoint.lastCurrentMeters());
            if (delta.signum() < 0) {
                delta = current.max(BigDecimal.ZERO);
                epoch++;
                quality = "ESTIMATED";
            }
        }
        if (delta.signum() < 0) {
            delta = ZERO;
        }
        long elapsedMillis = Duration.between(checkpoint.lastEventTime(), eventTime).toMillis();
        if (delta.signum() > 0 && elapsedMillis > 0) {
            double metersPerSecond = delta.doubleValue() * 1000.0 / elapsedMillis;
            if (metersPerSecond > properties.getMaxSpeedMps()) {
                quality = "SUSPECT";
            }
        }

        jdbcTemplate.update("""
                        UPDATE control_device_mileage_checkpoint
                        SET last_total_mileage_m = ?, last_current_mileage_m = ?, last_event_time = ?,
                            last_message_id = ?, mileage_epoch = ?, updated_at = ?
                        WHERE robot_id = ?
                        """,
                total != null ? total : checkpoint.lastTotalMeters(),
                current != null ? current : checkpoint.lastCurrentMeters(),
                Timestamp.valueOf(eventTime), reading.messageId(), epoch,
                Timestamp.valueOf(LocalDateTime.now(CHINA_ZONE)), reading.robotId());
        if (delta.signum() > 0 && !"SUSPECT".equals(quality) && !"RESET".equals(quality)) {
            LocalDateTime bucketTime = eventTime.truncatedTo(ChronoUnit.MINUTES);
            jdbcTemplate.update("""
                            INSERT INTO control_device_mileage_bucket
                            (robot_id, bucket_time, map_id, mileage_m, sample_count, quality, updated_at)
                            VALUES (?, ?, ?, ?, 1, 'NORMAL', ?)
                            ON DUPLICATE KEY UPDATE
                              mileage_m = mileage_m + VALUES(mileage_m),
                              sample_count = sample_count + 1,
                              updated_at = VALUES(updated_at)
                            """,
                    reading.robotId(), Timestamp.valueOf(bucketTime), value(reading.mapId()), delta,
                    Timestamp.valueOf(LocalDateTime.now(CHINA_ZONE)));
        }
        return new MileageResult(delta.setScale(3, RoundingMode.HALF_UP), quality, true);
    }

    private Checkpoint checkpoint(String robotId) {
        List<Checkpoint> rows = jdbcTemplate.query("""
                        SELECT last_total_mileage_m, last_current_mileage_m, last_event_time,
                               last_message_id, mileage_epoch
                        FROM control_device_mileage_checkpoint
                        WHERE robot_id = ? FOR UPDATE
                        """,
                (resultSet, rowNum) -> new Checkpoint(
                        resultSet.getBigDecimal("last_total_mileage_m"),
                        resultSet.getBigDecimal("last_current_mileage_m"),
                        resultSet.getTimestamp("last_event_time").toLocalDateTime(),
                        resultSet.getString("last_message_id"),
                        resultSet.getLong("mileage_epoch")),
                robotId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void publishIfNeeded(MileageReading reading, MileageResult result) {
        if (!result.accepted() || result.deltaMeters().signum() <= 0 || "SUSPECT".equals(result.quality())) {
            return;
        }
        BigDecimal accumulated = unpublishedMeters.merge(
                reading.robotId(), result.deltaMeters(), BigDecimal::add);
        BigDecimal threshold = BigDecimal.valueOf(Math.max(0.1, properties.getPublishDistanceThresholdMeters()));
        if (accumulated.compareTo(threshold) < 0) {
            return;
        }
        unpublishedMeters.put(reading.robotId(), ZERO);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("robotId", reading.robotId());
        event.put("deltaMeters", accumulated.setScale(3, RoundingMode.HALF_UP));
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
        return value == null || value.signum() < 0 ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record Checkpoint(
            BigDecimal lastTotalMeters,
            BigDecimal lastCurrentMeters,
            LocalDateTime lastEventTime,
            String lastMessageId,
            long epoch) {
    }

    public record MileageResult(BigDecimal deltaMeters, String quality, boolean accepted) {
        static MileageResult ignored(String quality) {
            return new MileageResult(ZERO, quality, false);
        }
    }
}
