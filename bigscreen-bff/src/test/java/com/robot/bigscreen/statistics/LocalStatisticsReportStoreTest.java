package com.robot.bigscreen.statistics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStatisticsReportStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadsReportFromPersistentDirectoryAfterInstanceRestart() {
        StatisticsReportStore.ReportOwner owner = new StatisticsReportStore.ReportOwner("user-1", "org-1");
        LocalStatisticsReportStore first = new LocalStatisticsReportStore(
                new ObjectMapper(), tempDir.toString(), 0, 1);
        StatisticsReportStore.ReportRecord record = first.save(
                new StatisticsReportStore.ReportDraft(
                        "统计报告", "统计报告.pdf", LocalDateTime.of(2026, 8, 24, 10, 0), owner),
                new byte[]{1, 2, 3});

        LocalStatisticsReportStore restarted = new LocalStatisticsReportStore(
                new ObjectMapper(), tempDir.toString(), 0, 1);

        assertEquals(1, restarted.list(owner).size());
        assertArrayEquals(new byte[]{1, 2, 3}, restarted.find(record.id(), owner).bytes());
    }

    @Test
    void refusesMultiInstanceConfiguration() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new LocalStatisticsReportStore(new ObjectMapper(), tempDir.toString(), 0, 2));
        assertTrue(exception.getMessage().contains("只允许单实例"));
    }

    @Test
    void refusesNewReportWhenReservedDiskSpaceCannotBeKept() {
        LocalStatisticsReportStore store = new LocalStatisticsReportStore(
                new ObjectMapper(), tempDir.toString(), Long.MAX_VALUE / 4, 1);
        StatisticsReportStore.ReportOwner owner = new StatisticsReportStore.ReportOwner("user-1", "org-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.save(
                        new StatisticsReportStore.ReportDraft(
                                "统计报告", "统计报告.pdf", LocalDateTime.now(), owner),
                        new byte[]{1}));

        assertTrue(exception.getMessage().contains("磁盘可用空间不足"));
    }
}
