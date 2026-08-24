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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS control_device_mileage_quality_event (
                    robot_id VARCHAR(128) NOT NULL,
                    event_time TIMESTAMP(3) NOT NULL,
                    message_id VARCHAR(160) NULL,
                    map_id VARCHAR(128) NOT NULL DEFAULT '',
                    delta_m DECIMAL(18,3) NOT NULL DEFAULT 0,
                    quality VARCHAR(20) NOT NULL,
                    elapsed_ms BIGINT NOT NULL DEFAULT 0,
                    speed_mps DECIMAL(18,3) NULL,
                    created_at TIMESTAMP(3) NOT NULL,
                    PRIMARY KEY (robot_id, event_time),
                    INDEX idx_mileage_quality_time (event_time),
                    INDEX idx_mileage_quality_value_time (quality, event_time)
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
        if ("RESET".equals(result.quality())
                || "ESTIMATED".equals(result.quality())
                || "SUSPECT".equals(result.quality())) {
            log.warn("检测到里程质量异常，机器人ID={} 质量={} 事件时间={} 增量米数={}",
                    reading.robotId(), result.quality(), DateTimeConfig.format(reading.eventTime()), result.deltaMeters());
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

        List<Map<String, Object>> distanceRows = jdbcTemplate.query(
                "SELECT robot_id, SUM(mileage_m) AS mileage_m, SUM(sample_count) AS sample_count, "
                        + "SUM(CASE WHEN quality = 'NORMAL' THEN mileage_m ELSE 0 END) AS normal_m, "
                        + "SUM(CASE WHEN quality = 'ESTIMATED' THEN mileage_m ELSE 0 END) AS estimated_m "
                        + "FROM control_device_mileage_bucket "
                        + "WHERE bucket_time >= ? AND bucket_time <= ? AND quality IN ('NORMAL', 'ESTIMATED')"
                        + filter + " GROUP BY robot_id ORDER BY robot_id",
                (resultSet, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("robotId", resultSet.getString("robot_id"));
                    row.put("mileageMeters", resultSet.getBigDecimal("mileage_m"));
                    row.put("sampleCount", resultSet.getLong("sample_count"));
                    row.put("normalMeters", resultSet.getBigDecimal("normal_m"));
                    row.put("estimatedMeters", resultSet.getBigDecimal("estimated_m"));
                    return row;
                },
                rangeArgs.toArray());
        List<Map<String, Object>> qualityRows = jdbcTemplate.query(
                "SELECT robot_id, MIN(event_time) AS observed_start, MAX(event_time) AS observed_end, "
                        + "SUM(CASE WHEN quality = 'NORMAL' THEN 1 ELSE 0 END) AS normal_count, "
                        + "SUM(CASE WHEN quality = 'ESTIMATED' THEN 1 ELSE 0 END) AS estimated_count, "
                        + "SUM(CASE WHEN quality = 'RESET' THEN 1 ELSE 0 END) AS reset_count, "
                        + "SUM(CASE WHEN quality = 'SUSPECT' THEN 1 ELSE 0 END) AS suspect_count, "
                        + "SUM(CASE WHEN quality = 'NORMAL' THEN delta_m ELSE 0 END) AS normal_event_m, "
                        + "SUM(CASE WHEN quality = 'ESTIMATED' THEN delta_m ELSE 0 END) AS estimated_event_m, "
                        + "SUM(CASE WHEN quality = 'SUSPECT' THEN delta_m ELSE 0 END) AS excluded_suspect_m "
                        + "FROM control_device_mileage_quality_event "
                        + "WHERE event_time >= ? AND event_time <= ?" + filter
                        + " GROUP BY robot_id ORDER BY robot_id",
                (resultSet, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("robotId", resultSet.getString("robot_id"));
                    row.put("observedStartTime", resultSet.getTimestamp("observed_start").toLocalDateTime());
                    row.put("observedEndTime", resultSet.getTimestamp("observed_end").toLocalDateTime());
                    row.put("normalCount", resultSet.getLong("normal_count"));
                    row.put("estimatedCount", resultSet.getLong("estimated_count"));
                    row.put("resetCount", resultSet.getLong("reset_count"));
                    row.put("suspectCount", resultSet.getLong("suspect_count"));
                    row.put("normalMeters", resultSet.getBigDecimal("normal_event_m"));
                    row.put("estimatedMeters", resultSet.getBigDecimal("estimated_event_m"));
                    row.put("excludedSuspectMeters", resultSet.getBigDecimal("excluded_suspect_m"));
                    return row;
                },
                rangeArgs.toArray());

        Map<String, Map<String, Object>> distances = indexByRobot(distanceRows);
        Map<String, Map<String, Object>> qualities = indexByRobot(qualityRows);
        List<String> resultRobotIds = normalizedRobotIds.isEmpty()
                ? java.util.stream.Stream.concat(distances.keySet().stream(), qualities.keySet().stream())
                        .distinct().sorted().toList()
                : normalizedRobotIds;
        List<Map<String, Object>> byRobot = new ArrayList<>();
        BigDecimal total = ZERO;
        BigDecimal normalTotal = ZERO;
        BigDecimal estimatedTotal = ZERO;
        BigDecimal excludedSuspectTotal = ZERO;
        long sampleCount = 0;
        long normalCount = 0;
        long estimatedCount = 0;
        long resetCount = 0;
        long suspectCount = 0;
        LocalDateTime observedStart = null;
        LocalDateTime observedEnd = null;
        for (String robotId : resultRobotIds) {
            Map<String, Object> distance = distances.get(robotId);
            Map<String, Object> quality = qualities.get(robotId);
            long robotSamples = longValue(distance, "sampleCount");
            BigDecimal robotTotal = decimalValue(distance, "mileageMeters");
            BigDecimal robotNormal = decimalValue(quality, "normalMeters");
            BigDecimal robotEstimated = decimalValue(quality, "estimatedMeters");
            BigDecimal robotExcluded = decimalValue(quality, "excludedSuspectMeters");
            long robotNormalCount = longValue(quality, "normalCount");
            long robotEstimatedCount = longValue(quality, "estimatedCount");
            long robotResetCount = longValue(quality, "resetCount");
            long robotSuspectCount = longValue(quality, "suspectCount");
            LocalDateTime robotStart = dateTimeValue(quality, "observedStartTime");
            LocalDateTime robotEnd = dateTimeValue(quality, "observedEndTime");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("robotId", robotId);
            item.put("hasData", robotSamples > 0);
            item.put("mileageMeters", robotSamples > 0 ? scale(robotTotal) : null);
            item.put("normalMeters", scale(robotNormal));
            item.put("estimatedMeters", scale(robotEstimated));
            item.put("excludedSuspectMeters", scale(robotExcluded));
            item.put("sampleCount", robotSamples);
            item.put("quality", quality(robotSamples, robotNormalCount, robotEstimatedCount, robotResetCount, robotSuspectCount));
            item.put("qualityCounts", qualityCounts(robotNormalCount, robotEstimatedCount, robotResetCount, robotSuspectCount));
            item.put("observedStartTime", robotStart == null ? null : DateTimeConfig.format(robotStart));
            item.put("observedEndTime", robotEnd == null ? null : DateTimeConfig.format(robotEnd));
            byRobot.add(item);

            total = total.add(robotTotal);
            normalTotal = normalTotal.add(robotNormal);
            estimatedTotal = estimatedTotal.add(robotEstimated);
            excludedSuspectTotal = excludedSuspectTotal.add(robotExcluded);
            sampleCount += robotSamples;
            normalCount += robotNormalCount;
            estimatedCount += robotEstimatedCount;
            resetCount += robotResetCount;
            suspectCount += robotSuspectCount;
            if (robotStart != null && (observedStart == null || robotStart.isBefore(observedStart))) {
                observedStart = robotStart;
            }
            if (robotEnd != null && (observedEnd == null || robotEnd.isAfter(observedEnd))) {
                observedEnd = robotEnd;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("startTime", DateTimeConfig.format(startTime));
        response.put("endTime", DateTimeConfig.format(endTime));
        response.put("timezone", CHINA_ZONE.getId());
        response.put("hasData", sampleCount > 0);
        response.put("totalMeters", sampleCount > 0 ? scale(total) : null);
        response.put("normalMeters", scale(normalTotal));
        response.put("estimatedMeters", scale(estimatedTotal));
        response.put("excludedSuspectMeters", scale(excludedSuspectTotal));
        response.put("sampleCount", sampleCount);
        response.put("quality", quality(sampleCount, normalCount, estimatedCount, resetCount, suspectCount));
        response.put("qualityCounts", qualityCounts(normalCount, estimatedCount, resetCount, suspectCount));
        response.put("observedStartTime", observedStart == null ? null : DateTimeConfig.format(observedStart));
        response.put("observedEndTime", observedEnd == null ? null : DateTimeConfig.format(observedEnd));
        response.put("unit", "m");
        response.put("byRobot", byRobot);
        return response;
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
        BigDecimal speedMetersPerSecond = null;
        if (delta.signum() > 0 && elapsedMillis > 0) {
            double metersPerSecond = delta.doubleValue() * 1000.0 / elapsedMillis;
            speedMetersPerSecond = BigDecimal.valueOf(metersPerSecond).setScale(3, RoundingMode.HALF_UP);
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
                            VALUES (?, ?, ?, ?, 1, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              mileage_m = mileage_m + VALUES(mileage_m),
                              sample_count = sample_count + 1,
                              quality = CASE
                                WHEN quality = 'ESTIMATED' OR ? = 'ESTIMATED' THEN 'ESTIMATED'
                                ELSE 'NORMAL'
                              END,
                              updated_at = VALUES(updated_at)
                            """,
                    reading.robotId(), Timestamp.valueOf(bucketTime), value(reading.mapId()), delta,
                    quality, Timestamp.valueOf(LocalDateTime.now(CHINA_ZONE)), quality);
        }
        jdbcTemplate.update("""
                        INSERT INTO control_device_mileage_quality_event
                        (robot_id, event_time, message_id, map_id, delta_m, quality,
                         elapsed_ms, speed_mps, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                reading.robotId(), Timestamp.valueOf(eventTime), reading.messageId(), value(reading.mapId()),
                delta, quality, elapsedMillis, speedMetersPerSecond,
                Timestamp.valueOf(LocalDateTime.now(CHINA_ZONE)));
        return new MileageResult(delta.setScale(3, RoundingMode.HALF_UP), quality, true);
    }

    private Map<String, Map<String, Object>> indexByRobot(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("robotId"), row);
        }
        return result;
    }

    private BigDecimal decimalValue(Map<String, Object> row, String key) {
        if (row == null || !(row.get(key) instanceof BigDecimal value)) {
            return ZERO;
        }
        return value;
    }

    private long longValue(Map<String, Object> row, String key) {
        if (row == null || !(row.get(key) instanceof Number value)) {
            return 0;
        }
        return value.longValue();
    }

    private LocalDateTime dateTimeValue(Map<String, Object> row, String key) {
        return row != null && row.get(key) instanceof LocalDateTime value ? value : null;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private String quality(long samples, long normal, long estimated, long reset, long suspect) {
        if (suspect > 0) {
            return "SUSPECT";
        }
        if (reset > 0) {
            return "RESET";
        }
        if (estimated > 0) {
            return "ESTIMATED";
        }
        if (normal > 0) {
            return "NORMAL";
        }
        return samples > 0 ? "UNKNOWN" : "NO_DATA";
    }

    private Map<String, Long> qualityCounts(long normal, long estimated, long reset, long suspect) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("NORMAL", normal);
        counts.put("ESTIMATED", estimated);
        counts.put("RESET", reset);
        counts.put("SUSPECT", suspect);
        return counts;
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
