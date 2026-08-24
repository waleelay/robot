package com.robot.bigscreen.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 单实例报告存储；使用持久化目录并以原子索引维护报告历史。 */
@Component
public class LocalStatisticsReportStore implements StatisticsReportStore {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_REPORTS_PER_USER = 100;
    private static final int RETENTION_DAYS = 30;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ReportRecord> records = new ConcurrentSkipListMap<>();
    private final ObjectMapper objectMapper;
    private final Path storageDir;
    private final Path indexPath;
    private final long minimumFreeBytes;

    @Autowired
    public LocalStatisticsReportStore(
            ObjectMapper objectMapper,
            @Value("${statistics.report.storage-dir:data/statistics-reports}") String storageDir,
            @Value("${statistics.report.minimum-free-bytes:1073741824}") long minimumFreeBytes,
            @Value("${statistics.report.instance-count:1}") int instanceCount) {
        if (instanceCount != 1) {
            throw new IllegalStateException("本地统计报告存储只允许单实例运行，多实例部署必须先实施共享存储");
        }
        this.objectMapper = objectMapper;
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
        this.indexPath = this.storageDir.resolve("index.json");
        this.minimumFreeBytes = Math.max(0, minimumFreeBytes);
        load();
    }

    LocalStatisticsReportStore(ObjectMapper objectMapper, String storageDir) {
        this(objectMapper, storageDir, 0, 1);
    }

    @Override
    public synchronized ReportRecord save(ReportDraft draft, byte[] bytes) {
        String id = String.valueOf(sequence.incrementAndGet());
        String objectKey = id + ".pdf";
        ReportRecord record = new ReportRecord(
                id, draft.reportName(), draft.filename(), draft.createdAt(), "PDF", "COMPLETED", objectKey,
                draft.owner().userId(), draft.owner().orgId());
        Path path = resolve(objectKey);
        ensureCapacity(bytes.length);
        write(path, bytes);
        try {
            records.put(id, record);
            cleanup(draft.owner(), draft.createdAt());
            persist();
            return record;
        } catch (RuntimeException exception) {
            records.remove(id);
            deleteFile(path);
            throw exception;
        }
    }

    @Override
    public synchronized List<ReportRecord> list(ReportOwner owner) {
        return records.values().stream()
                .filter(record -> record.ownedBy(owner) && "COMPLETED".equals(record.status()))
                .sorted(Comparator.comparing(ReportRecord::createdAt).thenComparing(ReportRecord::id).reversed())
                .toList();
    }

    @Override
    public synchronized StoredReport find(String id, ReportOwner owner) {
        ReportRecord record = records.get(id);
        if (record == null || !record.ownedBy(owner) || !"COMPLETED".equals(record.status())) {
            return null;
        }
        Path path = resolve(record.objectKey());
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return new StoredReport(record, Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地统计报告失败", exception);
        }
    }

    @Override
    public synchronized boolean delete(String id, ReportOwner owner) {
        ReportRecord record = records.get(id);
        if (record == null || !record.ownedBy(owner)) {
            return false;
        }
        deleteFile(resolve(record.objectKey()));
        records.remove(id);
        persist();
        return true;
    }

    @Override
    public synchronized void cleanupExpired() {
        cleanup(null, LocalDateTime.now());
        persist();
    }

    private void cleanup(ReportOwner owner, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RETENTION_DAYS);
        List<ReportRecord> targets = new ArrayList<>(records.values().stream()
                .filter(record -> record.createdAt().isBefore(cutoff))
                .toList());
        if (owner != null) {
            targets.addAll(records.values().stream()
                    .filter(record -> record.ownedBy(owner))
                    .sorted(Comparator.comparing(ReportRecord::createdAt).thenComparing(ReportRecord::id).reversed())
                    .skip(MAX_REPORTS_PER_USER)
                    .toList());
        }
        targets.stream().distinct().forEach(record -> {
            deleteFile(resolve(record.objectKey()));
            records.remove(record.id(), record);
        });
    }

    private void load() {
        try {
            Files.createDirectories(storageDir);
            if (!Files.exists(indexPath)) {
                return;
            }
            List<Map<String, Object>> rows = objectMapper.readValue(indexPath.toFile(), new TypeReference<>() {
            });
            long maxId = 0;
            for (Map<String, Object> row : rows) {
                String id = text(row.get("id"));
                String objectKey = text(row.get("filePath"));
                if (id == null || objectKey == null) {
                    continue;
                }
                ReportRecord record = new ReportRecord(
                        id,
                        defaultText(row.get("reportName"), "具身智能平台统计报告"),
                        defaultText(row.get("filename"), id + ".pdf"),
                        dateTime(row.get("downloadTime")),
                        defaultText(row.get("format"), "PDF"),
                        defaultText(row.get("status"), "COMPLETED"),
                        objectKey,
                        text(row.get("createdBy")),
                        text(row.get("orgId")));
                records.put(id, record);
                try {
                    maxId = Math.max(maxId, Long.parseLong(id));
                } catch (NumberFormatException ignored) {
                    // 新版共享存储使用 UUID；本地旧索引只对数字 ID 续号。
                }
            }
            sequence.set(maxId);
        } catch (IOException exception) {
            throw new IllegalStateException("加载本地统计报告索引失败", exception);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storageDir);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ReportRecord record : records.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", record.id());
                row.put("reportName", record.reportName());
                row.put("filename", record.filename());
                row.put("downloadTime", record.createdAt().format(FORMATTER));
                row.put("format", record.format());
                row.put("status", record.status());
                row.put("filePath", record.objectKey());
                row.put("createdBy", record.createdBy());
                row.put("orgId", record.orgId());
                rows.add(row);
            }
            Path temporary = indexPath.resolveSibling(indexPath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), rows);
            try {
                Files.move(temporary, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temporary, indexPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("保存本地统计报告索引失败", exception);
        }
    }

    private Path resolve(String objectKey) {
        Path path = storageDir.resolve(objectKey).normalize();
        if (!path.startsWith(storageDir)) {
            throw new IllegalArgumentException("统计报告对象键越界");
        }
        return path;
    }

    private void write(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("写入本地统计报告失败", exception);
        }
    }

    private void ensureCapacity(long reportBytes) {
        try {
            Files.createDirectories(storageDir);
            FileStore fileStore = Files.getFileStore(storageDir);
            long requiredBytes = Math.addExact(minimumFreeBytes, Math.max(0, reportBytes));
            if (fileStore.getUsableSpace() < requiredBytes) {
                throw new IllegalStateException("统计报告磁盘可用空间不足，已拒绝生成新报告");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("检查统计报告磁盘容量失败", exception);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("统计报告磁盘容量配置无效", exception);
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("删除本地统计报告失败", exception);
        }
    }

    private String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return result == null || result.isEmpty() ? null : result;
    }

    private String defaultText(Object value, String fallback) {
        String result = text(value);
        return result == null ? fallback : result;
    }

    private LocalDateTime dateTime(Object value) {
        try {
            return LocalDateTime.parse(defaultText(value, "1970-01-01 00:00:00"), FORMATTER);
        } catch (DateTimeParseException exception) {
            return LocalDateTime.MIN;
        }
    }
}
