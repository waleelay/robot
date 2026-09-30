package com.robot.bigscreen.statistics;

import java.time.LocalDateTime;
import java.util.List;

/** 统计报告元数据与 PDF 内容的统一存储边界。 */
public interface StatisticsReportStore {

    /**
     * 将报告元数据与二进制内容按归属保存。
     *
     * @param draft 待保存报告的名称、文件名、创建时间及归属
     * @param bytes 内容的原始二进制字节
     * @return 可用于历史列表和下载的报告记录
     */
    ReportRecord save(ReportDraft draft, byte[] bytes);

    /**
     * 列出当前归属范围内可见的报告。
     *
     * @param owner 当前资源或处理租约的持有者
     * @return 按存储实现约定排序的报告记录
     */
    List<ReportRecord> list(ReportOwner owner);

    /**
     * 在指定归属范围内查找报告及文件内容。
     *
     * @param id 当前业务记录的唯一标识
     * @param owner 当前资源或处理租约的持有者
     * @return 可见报告；不存在或不属于当前用户时为空
     */
    StoredReport find(String id, ReportOwner owner);

    /**
     * 仅删除指定归属范围内的报告及对应文件。
     *
     * @param id 当前业务记录的唯一标识
     * @param owner 当前资源或处理租约的持有者
     * @return 是否实际删除了报告
     */
    boolean delete(String id, ReportOwner owner);

    /**
     * 清理超过保留期限的报告元数据和文件。
     */
    void cleanupExpired();

    /**
     * 报告所属用户和组织，用于限定文件访问范围。
     *
     * @param userId 用户 ID
     * @param orgId 组织 ID
     */
    record ReportOwner(String userId, String orgId) {
    }

    /**
     * 生成报告前的名称、文件名、创建时间和所属身份。
     *
     * @param reportName 对用户展示的报告名称
     * @param filename 报告或下载文件的名称
     * @param createdAt 创建时间
     * @param owner 当前资源或处理租约的持有者
     */
    record ReportDraft(String reportName, String filename, LocalDateTime createdAt, ReportOwner owner) {
    }

    /**
     * 报告索引元数据，保留存储位置、生成状态及用户组织归属。
     * @param id 当前业务记录的唯一标识
     * @param reportName 对用户展示的报告名称
     * @param filename 报告或下载文件的名称
     * @param createdAt 创建时间
     * @param format 内容或导出文件格式
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param objectKey 报告存储中的相对文件键；本地实现据此定位 PDF
     * @param createdBy 创建该记录的用户标识
     * @param orgId 组织 ID
     */
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

    /**
     * 已读取的报告索引和文件字节，供授权下载返回。
     *
     * @param record 已保存的报告元数据
     * @param bytes 内容的原始二进制字节
     */
    record StoredReport(ReportRecord record, byte[] bytes) {
    }
}
