package com.robot.bigscreen.statistics;

import java.time.LocalDateTime;
import java.util.List;

/** 统计报告元数据与 PDF 内容的统一存储边界。 */
public interface StatisticsReportStore {

    ReportRecord save(ReportDraft draft, byte[] bytes);

    List<ReportRecord> list(ReportOwner owner);

    StoredReport find(String id, ReportOwner owner);

    boolean delete(String id, ReportOwner owner);

    void cleanupExpired();

    record ReportOwner(String userId, String orgId) {
    }

    record ReportDraft(String reportName, String filename, LocalDateTime createdAt, ReportOwner owner) {
    }

    record ReportRecord(
            String id,
            String reportName,
            String filename,
            LocalDateTime createdAt,
            String format,
            String status,
            String objectKey,
            String createdBy,
            String orgId) {

        boolean ownedBy(ReportOwner owner) {
            return owner != null
                    && createdBy != null
                    && createdBy.equals(owner.userId())
                    && java.util.Objects.equals(orgId, owner.orgId());
        }
    }

    record StoredReport(ReportRecord record, byte[] bytes) {
    }
}
