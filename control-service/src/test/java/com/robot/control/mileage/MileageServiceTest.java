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

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:mileage-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        MileageProperties properties = new MileageProperties();
        properties.setMaxSpeedMps(15);
        properties.setPublishDistanceThresholdMeters(10);
        service = new MileageService(
                jdbcTemplate,
                new DataSourceTransactionManager(dataSource),
                properties,
                mock(MediaWebSocketPublisher.class));
        service.initializeSchema();
    }

    @Test
    void establishesBaselineAndAccumulatesTotalMileageDelta() {
        assertThat(record("robot-1", "m1", 0, "5578.563", "397.672").quality()).isEqualTo("BASELINE");
        Map<String, Object> baselineSummary = summary("robot-1");
        assertThat(baselineSummary.get("hasData")).isEqualTo(false);
        assertThat(baselineSummary.get("totalMeters")).isNull();
        assertThat(baselineSummary.get("quality")).isEqualTo("NO_DATA");

        assertThat(record("robot-1", "m2", 10, "5583.563", "402.672").deltaMeters())
                .isEqualByComparingTo("5.000");

        Map<String, Object> summary = summary("robot-1");
        assertThat(summary.get("hasData")).isEqualTo(true);
        assertThat((BigDecimal) summary.get("totalMeters")).isEqualByComparingTo("5.000");
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
    void usesTotalMileageAcrossRebootAndCurrentMileageAsFallback() {
        record("robot-total", "m1", 0, "100", "30");
        record("robot-total", "m2", 10, "103", "1");
        assertThat((BigDecimal) summary("robot-total").get("totalMeters")).isEqualByComparingTo("3.000");

        record("robot-current", "c1", 0, null, "30");
        record("robot-current", "c2", 10, null, "35");
        MileageService.MileageResult reboot = record("robot-current", "c3", 20, null, "2");
        assertThat(reboot.quality()).isEqualTo("ESTIMATED");
        Map<String, Object> currentSummary = summary("robot-current");
        assertThat((BigDecimal) currentSummary.get("totalMeters")).isEqualByComparingTo("7.000");
        assertThat(currentSummary.get("quality")).isEqualTo("ESTIMATED");
        assertThat((BigDecimal) currentSummary.get("estimatedMeters")).isEqualByComparingTo("2.000");
    }

    @Test
    void excludesTotalMileageRollbackAndImpossibleJump() {
        record("robot-1", "m1", 0, "100", "20");
        assertThat(record("robot-1", "m2", 10, "90", "1").quality()).isEqualTo("RESET");
        assertThat(record("robot-1", "m3", 11, "190", "101").quality()).isEqualTo("SUSPECT");

        Map<String, Object> summary = summary("robot-1");
        assertThat(summary.get("hasData")).isEqualTo(false);
        assertThat(summary.get("totalMeters")).isNull();
        assertThat(summary.get("quality")).isEqualTo("SUSPECT");
        assertThat((BigDecimal) summary.get("excludedSuspectMeters")).isEqualByComparingTo("100.000");
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
                decimal(current),
                "map-1"));
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
