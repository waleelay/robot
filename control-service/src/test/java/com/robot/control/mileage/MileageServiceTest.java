package com.robot.control.mileage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.robot.control.ws.MediaWebSocketPublisher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MileageServiceTest {

    private MileageService service;
    private JdbcTemplate jdbcTemplate;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:mileage-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = newService();
        service.initializeSchema();
    }

    @Test
    void establishesBaselineAndAccumulatesDelta() {
        assertThat(record("robot-1", "m1", 0, "5578.563", "397.672").quality()).isEqualTo("BASELINE");
        assertThat(summary("robot-1").get("totalMeters")).isNull();

        assertThat(record("robot-1", "m2", 10, "5583.563", "402.672").deltaMeters())
                .isEqualByComparingTo("5.000");

        Map<String, Object> summary = summary("robot-1");
        assertThat((BigDecimal) summary.get("totalMeters")).isEqualByComparingTo("5.000");
        assertThat(summary.get("sampleCount")).isEqualTo(1L);
        assertThat(summary.get("quality")).isEqualTo("NORMAL");
    }

    @Test
    void ignoresDuplicateAndOutOfOrderMessages() {
        record("robot-1", "m1", 0, "100", "20");
        record("robot-1", "m2", 10, "105", "25");

        assertThat(record("robot-1", "m2", 10, "105", "25").accepted()).isFalse();
        assertThat(record("robot-1", "old", 5, "103", "23").accepted()).isFalse();
        assertThat((BigDecimal) summary("robot-1").get("totalMeters")).isEqualByComparingTo("5.000");
    }

    @Test
    void restoresCheckpointAfterRestartAndUsesCurrentMileageAsFallback() {
        record("robot-total", "m1", 0, "100", "30");
        service = newService();
        service.initializeSchema();
        record("robot-total", "m2", 10, "103", "1");
        assertThat((BigDecimal) summary("robot-total").get("totalMeters")).isEqualByComparingTo("3.000");

        record("robot-current", "c1", 0, null, "30");
        record("robot-current", "c2", 10, null, "35");
        assertThat(record("robot-current", "c3", 20, null, "2").quality()).isEqualTo("ESTIMATED");
        Map<String, Object> current = summary("robot-current");
        assertThat((BigDecimal) current.get("totalMeters")).isEqualByComparingTo("7.000");
        assertThat(current.get("quality")).isEqualTo("ESTIMATED");
    }

    @Test
    void excludesRollbackAndImpossibleJump() {
        record("robot-1", "m1", 0, "100", "20");

        assertThat(record("robot-1", "m2", 10, "90", "1").quality()).isEqualTo("RESET");
        assertThat(record("robot-1", "m3", 11, "190", "101").quality()).isEqualTo("SUSPECT");
        assertThat(summary("robot-1").get("totalMeters")).isNull();
        assertThat(summary("robot-1").get("quality")).isEqualTo("NO_DATA");
    }

    @Test
    void keepsLegacyBucketTotalAndMarksQualityUnknown() {
        insertLegacyBucket("legacy-1", LocalDateTime.of(2026, 8, 14, 10, 0), "5.000", 2);

        Map<String, Object> summary = summary("legacy-1");

        assertThat((BigDecimal) summary.get("totalMeters")).isEqualByComparingTo("5.000");
        assertThat(summary.get("sampleCount")).isEqualTo(2L);
        assertThat(summary.get("quality")).isEqualTo("UNKNOWN");
    }

    @Test
    void keepsMixedLegacyMinuteUnknownWithoutExtraTables() {
        recordAt("mixed-1", "baseline", LocalDateTime.of(2026, 8, 14, 9, 59, 50), "100", "20");
        insertLegacyBucket("mixed-1", LocalDateTime.of(2026, 8, 14, 10, 0), "5.000", 1);
        recordAt("mixed-1", "m2", LocalDateTime.of(2026, 8, 14, 10, 0, 10), "103", "23");

        Map<String, Object> summary = summary("mixed-1");

        assertThat((BigDecimal) summary.get("totalMeters")).isEqualByComparingTo("8.000");
        assertThat(summary.get("sampleCount")).isEqualTo(2L);
        assertThat(summary.get("quality")).isEqualTo("UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_NAME LIKE 'CONTROL_DEVICE_MILEAGE%'",
                Long.class)).isEqualTo(2L);
    }

    private MileageService newService() {
        MileageProperties properties = new MileageProperties();
        properties.setMaxSpeedMps(15);
        properties.setPublishDistanceThresholdMeters(10);
        return new MileageService(
                jdbcTemplate,
                new DataSourceTransactionManager(dataSource),
                properties,
                mock(MediaWebSocketPublisher.class));
    }

    private MileageService.MileageResult record(
            String robotId,
            String messageId,
            long seconds,
            String total,
            String current) {
        return service.record(new MileageReading(
                robotId,
                messageId,
                OffsetDateTime.of(2026, 8, 14, 10, 0, 0, 0, ZoneOffset.ofHours(8)).plusSeconds(seconds),
                decimal(total),
                decimal(current)));
    }

    private MileageService.MileageResult recordAt(
            String robotId,
            String messageId,
            LocalDateTime eventTime,
            String total,
            String current) {
        return service.record(new MileageReading(
                robotId,
                messageId,
                eventTime.atOffset(ZoneOffset.ofHours(8)),
                decimal(total),
                decimal(current)));
    }

    private void insertLegacyBucket(String robotId, LocalDateTime bucketTime, String mileage, long samples) {
        jdbcTemplate.update("""
                        INSERT INTO control_device_mileage_bucket
                        (robot_id, bucket_time, mileage_m, sample_count, quality, updated_at)
                        VALUES (?, ?, ?, ?, 'NORMAL', ?)
                        """,
                robotId, bucketTime, new BigDecimal(mileage), samples, bucketTime);
    }

    private Map<String, Object> summary(String robotId) {
        return service.summary(
                LocalDateTime.of(2026, 8, 14, 0, 0),
                LocalDateTime.of(2026, 8, 14, 23, 59, 59),
                List.of(robotId));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
