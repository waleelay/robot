package com.robot.bigscreen.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LENIENT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "statistics-io");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicLong reportId = new AtomicLong();
    private final Map<String, ReportHistory> reportHistories = new ConcurrentSkipListMap<>();
    private final Object historyLock = new Object();
    private final ObjectMapper objectMapper;
    private final PanoramaCenterClient centerClient;
    private final DeviceStatusSampler deviceStatusSampler;
    private final Path reportStorageDir;
    private final Path reportIndexPath;

    public StatisticsService(
            ObjectMapper objectMapper,
            PanoramaCenterClient centerClient,
            DeviceStatusSampler deviceStatusSampler,
            @Value("${statistics.report.storage-dir:data/statistics-reports}") String reportStorageDir) {
        this.objectMapper = objectMapper;
        this.centerClient = centerClient;
        this.deviceStatusSampler = deviceStatusSampler;
        this.reportStorageDir = Paths.get(reportStorageDir);
        this.reportIndexPath = this.reportStorageDir.resolve("index.json");
        loadReportHistories();
    }

    public Map<String, Object> overview(String range, String startTime, String endTime, String deviceType, String areaId) {
        return overview(range, startTime, endTime, deviceType, areaId, null);
    }

    private Map<String, Object> overview(
            String range,
            String startTime,
            String endTime,
            String deviceType,
            String areaId,
            List<Map<String, Object>> deviceTypeOptions) {
        Map<String, Object> normalizedRange = normalizedRange(range, startTime, endTime);
        LocalDateTime rangeStart = parseTime(stringValue(normalizedRange.get("startTime"), null));
        LocalDateTime rangeEnd = parseTime(stringValue(normalizedRange.get("endTime"), null));
        String normalizedDeviceType = blankToDefault(deviceType, "all");

        CompletableFuture<List<Map<String, Object>>> optionsFuture = deviceTypeOptions == null
                ? async(this::deviceTypeOptions)
                : CompletableFuture.completedFuture(deviceTypeOptions);
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(centerClient::devices);
        CompletableFuture<List<Map<String, Object>>> tasksFuture = async(centerClient::taskWorkflowInstancesForStatistics);
        CompletableFuture<List<Map<String, Object>>> alarmsFuture = async(() -> centerClient.alarmsForStatistics(
                stringValue(normalizedRange.get("startTime"), null),
                stringValue(normalizedRange.get("endTime"), null)));

        List<Map<String, Object>> resolvedDeviceTypeOptions = join(optionsFuture, List.of());
        List<Map<String, Object>> devices = join(devicesFuture, List.of());
        Map<String, String> deviceTypesBySerial = deviceTypesBySerial(devices);
        List<String> mileageRobotIds = devices.stream()
                .filter(device -> matchesDeviceType(device, normalizedDeviceType))
                .map(device -> firstString(device, "serialNumber", "robotId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        MileageWindow mileageWindow = mileageWindow(rangeStart, rangeEnd);
        CompletableFuture<Map<String, Object>> mileageFuture = mileageWindow == null || mileageRobotIds.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : async(() -> centerClient.mileageSummary(
                        mileageWindow.start().format(DATE_TIME_FORMATTER),
                        mileageWindow.end().format(DATE_TIME_FORMATTER),
                        mileageRobotIds));
        CompletableFuture<Map<String, Object>> previousMileageFuture = mileageWindow == null || mileageRobotIds.isEmpty()
                ? CompletableFuture.completedFuture(Map.of())
                : async(() -> centerClient.mileageSummary(
                        mileageWindow.previousStart().format(DATE_TIME_FORMATTER),
                        mileageWindow.previousEnd().format(DATE_TIME_FORMATTER),
                        mileageRobotIds));
        List<Map<String, Object>> tasks = join(tasksFuture, List.<Map<String, Object>>of()).stream()
                .filter(task -> withinRange(taskTime(task), rangeStart, rangeEnd))
                .filter(task -> matchesTaskDeviceType(task, normalizedDeviceType, deviceTypesBySerial))
                .toList();
        List<Map<String, Object>> alarms = join(alarmsFuture, List.<Map<String, Object>>of()).stream()
                .filter(alarm -> withinRange(alarmTime(alarm), rangeStart, rangeEnd))
                .filter(alarm -> matchesAlarmDeviceType(alarm, normalizedDeviceType, deviceTypesBySerial))
                .toList();

        Map<String, Object> taskCompletion = taskCompletion(tasks);
        return object(
                "serverTime", now(),
                "range", normalizedRange,
                "deviceTypeOptions", resolvedDeviceTypeOptions,
                "filters", object(
                        "deviceType", normalizedDeviceType,
                        "areaId", areaId),
                "kpis", kpis(
                        tasks,
                        alarms,
                        join(mileageFuture, Map.of()),
                        join(previousMileageFuture, Map.of())),
                "equipmentRuntime", equipmentRuntime(
                        devices, tasks, normalizedDeviceType, deviceTypesBySerial, resolvedDeviceTypeOptions,
                        rangeStart, rangeEnd),
                "aiAlarmAnalysis", aiAlarmAnalysis(alarms),
                "alarmAreaRanking", alarmAreaRanking(alarms),
                "alarmTrend", alarmTrend(alarms, rangeStart, rangeEnd),
                "taskCompletion", taskCompletion);
    }

    public byte[] exportPdf(Map<String, Object> request) {
        return createReport(request).bytes();
    }

    public ReportFile createReport(Map<String, Object> request) {
        ReportSelection selection = reportSelection(request);
        List<Map<String, Object>> deviceTypeOptions = deviceTypeOptions();
        Map<String, Object> data = overview(
                selection.rangeType(),
                selection.startTime(),
                selection.endTime(),
                selection.deviceType(),
                null,
                deviceTypeOptions);
        byte[] bytes = reportPdf(data, selection, deviceTypeOptions);
        String id = String.valueOf(reportId.incrementAndGet());
        String createdAt = now();
        String reportName = "具身智能平台统计报告-" + rangeLabel(selection.rangeType()) + "-"
                + deviceTypeName(selection.deviceType(), deviceTypeOptions);
        String filename = reportName + "-" + LocalDateTime.now(CHINA_ZONE).format(FILE_DATE_FORMATTER) + ".pdf";
        String storedFilename = id + ".pdf";
        Path reportPath = reportStorageDir.resolve(storedFilename);
        ReportHistory history = new ReportHistory(id, reportName, filename, createdAt, "PDF", "COMPLETED", storedFilename);
        synchronized (historyLock) {
            writeBytes(reportPath, bytes);
            reportHistories.put(id, history);
            saveReportHistories();
        }
        return new ReportFile(id, filename, bytes);
    }

    public Map<String, Object> reportHistoryList(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(size, 1);
        List<ReportHistory> reports;
        synchronized (historyLock) {
            reports = new ArrayList<>(reportHistories.values());
        }
        reports.sort((left, right) -> Long.compare(parseReportId(right.id()), parseReportId(left.id())));

        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, reports.size());
        int toIndex = Math.min(fromIndex + normalizedSize, reports.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReportHistory report : reports.subList(fromIndex, toIndex)) {
            rows.add(report.toResponse());
        }
        return object(
                "data", rows,
                "total", reports.size(),
                "page", normalizedPage,
                "size", normalizedSize);
    }

    public ReportFile reportFile(String id) {
        ReportHistory history = reportHistories.get(id);
        if (history == null) {
            return null;
        }
        Path reportPath = reportStorageDir.resolve(history.filePath()).normalize();
        if (!reportPath.startsWith(reportStorageDir.normalize()) || !Files.exists(reportPath)) {
            return null;
        }
        return new ReportFile(history.id(), history.filename(), readBytes(reportPath));
    }

    public boolean deleteReport(String id) {
        synchronized (historyLock) {
            ReportHistory history = reportHistories.remove(id);
            if (history == null) {
                return false;
            }
            deleteFile(reportStorageDir.resolve(history.filePath()));
            saveReportHistories();
            return true;
        }
    }

    private void loadReportHistories() {
        try {
            Files.createDirectories(reportStorageDir);
            if (!Files.exists(reportIndexPath)) {
                return;
            }
            List<Map<String, Object>> rows = objectMapper.readValue(
                    reportIndexPath.toFile(),
                    new TypeReference<>() {
                    });
            long maxId = 0;
            for (Map<String, Object> row : rows) {
                String id = stringValue(row.get("id"), null);
                String filePath = stringValue(row.get("filePath"), null);
                if (id == null || filePath == null) {
                    continue;
                }
                ReportHistory history = new ReportHistory(
                        id,
                        stringValue(row.get("reportName"), "具身智能平台统计报告"),
                        stringValue(row.get("filename"), id + ".pdf"),
                        stringValue(row.get("downloadTime"), "-"),
                        stringValue(row.get("format"), "PDF"),
                        stringValue(row.get("status"), "COMPLETED"),
                        filePath);
                reportHistories.put(id, history);
                maxId = Math.max(maxId, parseReportId(id));
            }
            reportId.set(maxId);
        } catch (IOException ex) {
            throw new IllegalStateException("加载历史报告索引失败: " + reportIndexPath, ex);
        }
    }

    private void saveReportHistories() {
        try {
            Files.createDirectories(reportStorageDir);
            List<Map<String, Object>> rows = new ArrayList<>();
            List<ReportHistory> reports = new ArrayList<>(reportHistories.values());
            reports.sort((left, right) -> Long.compare(parseReportId(left.id()), parseReportId(right.id())));
            for (ReportHistory report : reports) {
                rows.add(report.toIndexEntry());
            }
            Path tempPath = reportIndexPath.resolveSibling(reportIndexPath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), rows);
            try {
                Files.move(tempPath, reportIndexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tempPath, reportIndexPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存历史报告索引失败: " + reportIndexPath, ex);
        }
    }

    private void writeBytes(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("写入历史报告文件失败: " + path, ex);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("读取历史报告文件失败: " + path, ex);
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("删除历史报告文件失败: " + path, ex);
        }
    }

    private long parseReportId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, Object> normalizedRange(String range, String startTime, String endTime) {
        String type = blankToDefault(range, "month");
        LocalDate today = LocalDate.now(CHINA_ZONE);
        String normalizedStart = startTime;
        String normalizedEnd = endTime;
        if (!"custom".equals(type)) {
            LocalDate startDate;
            if ("today".equals(type)) {
                startDate = today;
            } else if ("week".equals(type)) {
                startDate = today.minusDays(6);
            } else if ("all".equals(type)) {
                startDate = today.minusMonths(6);
            } else {
                startDate = today.withDayOfMonth(1);
                type = "month";
            }
            normalizedStart = LocalDateTime.of(startDate, LocalTime.MIN).format(DATE_TIME_FORMATTER);
            normalizedEnd = LocalDateTime.of(today, LocalTime.MAX.withNano(0)).format(DATE_TIME_FORMATTER);
        }
        return object(
                "type", type,
                "startTime", normalizedStart,
                "endTime", normalizedEnd);
    }

    private Map<String, Object> kpis(
            List<Map<String, Object>> tasks,
            List<Map<String, Object>> alarms,
            Map<String, Object> mileage,
            Map<String, Object> previousMileage) {
        Map<String, Object> kpis = emptyKpis();
        kpis.put("taskTotal", kpi(tasks.size(), null));
        Double mileageKilometers = mileageKilometers(mileage);
        kpis.put("patrolMileage", kpi(
                mileageKilometers,
                mileageCompareRate(mileageMeters(mileage), mileageMeters(previousMileage))));
        kpis.put("aiAlarmTotal", kpi(alarms.size(), null));
        return kpis;
    }

    private MileageWindow mileageWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        long inclusiveSeconds = Duration.between(start, end).getSeconds() + 1;
        LocalDateTime previousEnd = start.minusSeconds(1);
        LocalDateTime previousStart = previousEnd.minusSeconds(Math.max(0, inclusiveSeconds - 1));
        return new MileageWindow(start, end, previousStart, previousEnd);
    }

    private Double mileageKilometers(Map<String, Object> summary) {
        Double meters = mileageMeters(summary);
        return meters == null ? null : oneDecimal(meters.doubleValue() / 1000.0);
    }

    private Double mileageMeters(Map<String, Object> summary) {
        if (summary == null || !Boolean.TRUE.equals(summary.get("hasData"))) {
            return null;
        }
        Number meters = numberValue(summary.get("totalMeters"));
        return meters == null ? null : meters.doubleValue();
    }

    private Double mileageCompareRate(Double current, Double previous) {
        if (current == null || previous == null) {
            return null;
        }
        if (previous == 0) {
            return current == 0 ? 0.0 : null;
        }
        return oneDecimal((current - previous) * 100.0 / previous);
    }

    private Map<String, Object> equipmentRuntime(
            List<Map<String, Object>> devices,
            List<Map<String, Object>> tasks,
            String deviceType,
            Map<String, String> deviceTypesBySerial,
            List<Map<String, Object>> deviceTypeOptions,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        List<Map<String, Object>> filteredDevices = devices.stream()
                .filter(device -> matchesDeviceType(device, deviceType))
                .toList();
        List<String> serialNumbers = filteredDevices.stream()
                .map(device -> firstString(device, "serialNumber", "robotId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<String> onlineSerialNumbers = new LinkedHashSet<>();
        for (Map<String, Object> status : centerClient.realtimeStatuses(serialNumbers)) {
            if (isOnline(firstString(status, "onlineStatus", "status"))) {
                String serialNumber = firstString(status, "serialNumber", "robotId");
                if (serialNumber != null) {
                    onlineSerialNumbers.add(serialNumber);
                }
            }
        }

        Long onlineRate = serialNumbers.isEmpty()
                ? null
                : Math.round(onlineSerialNumbers.size() * 100.0 / serialNumbers.size());
        long completed = tasks.stream().filter(task -> "COMPLETED".equals(taskStatusGroup(task))).count();
        Double completionRate = tasks.isEmpty() ? null : percentage(completed, tasks.size());
        List<Map<String, Object>> runtimeItems = equipmentRuntimeItems(
                devices, deviceType, deviceTypeOptions, rangeStart, rangeEnd);
        return object(
                "onlineRate", onlineRate,
                "taskCompletionRate", completionRate,
                "unit", runtimeItems.isEmpty() ? null : "小时",
                "items", runtimeItems);
    }

    /**
     * 按设备清单（管理端）枚举所有设备类型，运行/离线/故障时长来自后台 registry 采样
     * （采样跨度 × 状态占比折算）。未参与采样的设备时长为 0，但仍保留类型显示。
     */
    private List<Map<String, Object>> equipmentRuntimeItems(
            List<Map<String, Object>> devices,
            String selectedDeviceType,
            List<Map<String, Object>> deviceTypeOptions,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        Map<String, String> typeNames = new LinkedHashMap<>();
        for (Map<String, Object> option : deviceTypeOptions) {
            String value = firstString(option, "value");
            String label = firstString(option, "label");
            if (value != null && label != null) {
                typeNames.put(value.toUpperCase(Locale.ROOT), label);
            }
        }
        Map<String, List<Map<String, Object>>> devicesByType = new LinkedHashMap<>();
        for (Map<String, Object> device : devices) {
            String type = firstString(device, "deviceType", "typeCode", "type");
            if (type == null) {
                continue;
            }
            if (!"all".equalsIgnoreCase(selectedDeviceType) && !selectedDeviceType.equalsIgnoreCase(type)) {
                continue;
            }
            devicesByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(device);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : devicesByType.entrySet()) {
            String type = entry.getKey();
            double running = 0;
            double offline = 0;
            double fault = 0;
            for (Map<String, Object> device : entry.getValue()) {
                String serial = firstString(device, "serialNumber", "robotId");
                if (serial == null) {
                    continue;
                }
                long[] counts = deviceStatusSampler.countsInRange(serial, rangeStart, rangeEnd);
                long on = counts[0];
                long off = counts[1];
                long flt = counts[2];
                long spanSeconds = counts[3];
                long total = on + off + flt;
                if (total <= 0 || spanSeconds <= 0) {
                    continue;
                }
                running += spanSeconds * on / (double) total;
                offline += spanSeconds * off / (double) total;
                fault += spanSeconds * flt / (double) total;
            }
            items.add(object(
                    "deviceType", type,
                    "deviceTypeName", typeNames.getOrDefault(type.toUpperCase(Locale.ROOT), type),
                    "runningHours", oneDecimal(running / 3600.0),
                    "faultHours", oneDecimal(fault / 3600.0),
                    "offlineHours", oneDecimal(offline / 3600.0)));
        }
        items.sort(Comparator.comparing(item -> String.valueOf(item.get("deviceType"))));
        return items;
    }

    private Map<String, Object> aiAlarmAnalysis(List<Map<String, Object>> alarms) {
        return object(
                "alarmTypeRanking", rankedDistribution(alarms, alarm -> alarmTypeName(firstString(alarm, "alarmType"))),
                "handleMethodRanking", rankedDistribution(
                        alarms.stream().filter(alarm -> firstString(alarm, "handleResult") != null).toList(),
                        alarm -> handleMethodName(firstString(alarm, "handleResult"))));
    }

    private List<Map<String, Object>> alarmAreaRanking(List<Map<String, Object>> alarms) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> alarm : alarms) {
            String areaName = alarmAreaName(alarm);
            if (areaName != null) {
                counts.merge(areaName, 1L, Long::sum);
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> object(
                        "areaId", null,
                        "areaName", entry.getKey(),
                        "count", entry.getValue(),
                        "percent", percentage(entry.getValue(), total)))
                .toList();
    }

    private Map<String, Object> alarmTrend(
            List<Map<String, Object>> alarms,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd) {
        if (rangeStart == null || rangeEnd == null || rangeEnd.isBefore(rangeStart)) {
            return emptyAlarmTrend();
        }
        long days = ChronoUnit.DAYS.between(rangeStart.toLocalDate(), rangeEnd.toLocalDate()) + 1;
        if (days <= 1) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                counts.put(String.format(Locale.ROOT, "%02d:00", hour), 0L);
            }
            alarms.stream().map(this::alarmTime).filter(Objects::nonNull)
                    .forEach(time -> counts.computeIfPresent(
                            String.format(Locale.ROOT, "%02d:00", time.getHour()), (key, value) -> value + 1));
            return trend("次", counts);
        }
        if (days <= 62) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (LocalDate date = rangeStart.toLocalDate(); !date.isAfter(rangeEnd.toLocalDate()); date = date.plusDays(1)) {
                counts.put(date.format(DateTimeFormatter.ofPattern("MM.dd")), 0L);
            }
            alarms.stream().map(this::alarmTime).filter(Objects::nonNull)
                    .forEach(time -> counts.computeIfPresent(
                            time.format(DateTimeFormatter.ofPattern("MM.dd")), (key, value) -> value + 1));
            return trend("次", counts);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        LocalDate month = rangeStart.toLocalDate().withDayOfMonth(1);
        LocalDate endMonth = rangeEnd.toLocalDate().withDayOfMonth(1);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        while (!month.isAfter(endMonth)) {
            counts.put(month.format(monthFormatter), 0L);
            month = month.plusMonths(1);
        }
        alarms.stream().map(this::alarmTime).filter(Objects::nonNull)
                .forEach(time -> counts.computeIfPresent(time.format(monthFormatter), (key, value) -> value + 1));
        return trend("次", counts);
    }

    private Map<String, Object> trend(String unit, Map<String, Long> counts) {
        List<Map<String, Object>> points = counts.entrySet().stream()
                .map(entry -> object("label", entry.getKey(), "count", entry.getValue()))
                .toList();
        return object("unit", unit, "points", points);
    }

    private Map<String, Object> taskCompletion(List<Map<String, Object>> tasks) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("COMPLETED", "已完成");
        names.put("RUNNING", "执行中");
        names.put("PENDING", "待执行");
        names.put("INTERRUPTED", "异常中断");
        Map<String, Long> counts = new LinkedHashMap<>();
        names.keySet().forEach(status -> counts.put(status, 0L));
        tasks.forEach(task -> counts.computeIfPresent(taskStatusGroup(task), (key, value) -> value + 1));

        List<Map<String, Object>> items = counts.entrySet().stream()
                .map(entry -> object(
                        "status", entry.getKey(),
                        "name", names.get(entry.getKey()),
                        "count", entry.getValue(),
                        "percent", percentage(entry.getValue(), tasks.size())))
                .toList();
        long completed = counts.get("COMPLETED");
        String insight = tasks.isEmpty()
                ? null
                : "本周期共执行 " + tasks.size() + " 次任务，已完成 " + completed + " 次，完成率 "
                        + percentage(completed, tasks.size()) + "%";
        return object("items", items, "insight", insight);
    }

    private List<Map<String, Object>> rankedDistribution(
            List<Map<String, Object>> source,
            java.util.function.Function<Map<String, Object>, String> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        source.stream().map(classifier).filter(Objects::nonNull).forEach(name -> counts.merge(name, 1L, Long::sum));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> object(
                        "name", entry.getKey(),
                        "count", entry.getValue(),
                        "percent", percentage(entry.getValue(), total)))
                .toList();
    }

    private Map<String, String> deviceTypesBySerial(List<Map<String, Object>> devices) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> device : devices) {
            String serialNumber = firstString(device, "serialNumber", "robotId");
            String type = firstString(device, "deviceType", "typeCode", "type");
            if (serialNumber != null && type != null) {
                result.put(serialNumber, type);
            }
        }
        return result;
    }

    private boolean matchesDeviceType(Map<String, Object> device, String deviceType) {
        return "all".equalsIgnoreCase(deviceType)
                || deviceType.equalsIgnoreCase(firstString(device, "deviceType", "typeCode", "type"));
    }

    private boolean matchesTaskDeviceType(
            Map<String, Object> task,
            String deviceType,
            Map<String, String> deviceTypesBySerial) {
        if ("all".equalsIgnoreCase(deviceType)) {
            return true;
        }
        for (Map<String, Object> summary : mapList(task.get("deviceSummaries"))) {
            String summaryType = firstString(summary, "deviceType", "typeCode", "type");
            String serialNumber = firstString(summary, "serialNumber", "robotId", "deviceCode");
            if (deviceType.equalsIgnoreCase(summaryType)
                    || deviceType.equalsIgnoreCase(deviceTypesBySerial.get(serialNumber))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAlarmDeviceType(
            Map<String, Object> alarm,
            String deviceType,
            Map<String, String> deviceTypesBySerial) {
        if ("all".equalsIgnoreCase(deviceType)) {
            return true;
        }
        String serialNumber = firstString(alarm, "serialNumber", "robotId", "deviceCode");
        return deviceType.equalsIgnoreCase(deviceTypesBySerial.get(serialNumber));
    }

    private String taskStatusGroup(Map<String, Object> task) {
        String status = firstString(task, "status");
        if (status == null) {
            return "PENDING";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "COMPLETED", "SUCCESS", "SUCCEEDED", "FINISHED" -> "COMPLETED";
            case "RUNNING", "EXECUTING", "PAUSED", "SUSPENDED" -> "RUNNING";
            case "FAILED", "ERROR", "TERMINATED", "CANCELLED", "INTERRUPTED", "TIMEOUT" -> "INTERRUPTED";
            default -> "PENDING";
        };
    }

    private String alarmTypeName(String alarmType) {
        if (alarmType == null) {
            return "未分类";
        }
        return switch (alarmType.toUpperCase(Locale.ROOT)) {
            case "FIRE" -> "火灾告警";
            case "SMOKE" -> "烟雾告警";
            case "INTRUSION" -> "入侵告警";
            case "TEMPERATURE" -> "温度告警";
            default -> alarmType;
        };
    }

    private String handleMethodName(String handleResult) {
        if (handleResult == null) {
            return null;
        }
        return switch (handleResult.toUpperCase(Locale.ROOT)) {
            case "IMMEDIATE_DISPOSAL", "HANDLED", "CONFIRMED" -> "立即处置";
            case "FALSE_ALARM" -> "误报";
            default -> handleResult;
        };
    }

    private String alarmAreaName(Map<String, Object> alarm) {
        String direct = firstString(alarm, "areaName", "locationName", "address");
        if (direct != null) {
            return direct;
        }
        Map<String, Object> location = mapValue(alarm.get("location"));
        String locationAddress = firstString(location, "areaName", "name", "address");
        if (locationAddress != null) {
            return locationAddress;
        }
        Map<String, Object> rawPayload = rawPayload(alarm);
        Map<String, Object> rawLocation = mapValue(rawPayload.get("location"));
        return firstString(rawLocation, "areaName", "name", "address");
    }

    private Map<String, Object> rawPayload(Map<String, Object> source) {
        Object rawPayload = source.get("rawPayload");
        if (rawPayload instanceof Map<?, ?>) {
            return mapValue(rawPayload);
        }
        String text = stringValue(rawPayload, null);
        if (text == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private LocalDateTime taskTime(Map<String, Object> task) {
        return parseTime(firstString(task, "startedAt", "completedAt", "createdAt"));
    }

    private LocalDateTime alarmTime(Map<String, Object> alarm) {
        return parseTime(firstString(alarm, "occurredAt", "createdAt"));
    }

    private Long durationSeconds(Map<String, Object> task) {
        Number durationSeconds = numberValue(task.get("durationSeconds"));
        if (durationSeconds != null) {
            return Math.max(0L, Math.round(durationSeconds.doubleValue()));
        }
        LocalDateTime startedAt = parseTime(firstString(task, "startedAt"));
        LocalDateTime completedAt = parseTime(firstString(task, "completedAt"));
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toSeconds();
    }

    private Number numberValue(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 尝试不带时区的时间格式。
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            // 尝试大屏统一时间格式。
        }
        try {
            return LocalDateTime.parse(raw, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 兼容前端自定义时间未补零的格式，例如 2026-7-28 00:00:00。
        }
        try {
            return LocalDateTime.parse(raw, LENIENT_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean withinRange(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        if (time == null) {
            return false;
        }
        return (start == null || !time.isBefore(start)) && (end == null || !time.isAfter(end));
    }

    private boolean isOnline(String status) {
        return status != null && ("online".equalsIgnoreCase(status) || "在线".equals(status));
    }

    private Double percentage(long count, long total) {
        return total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 10.0;
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            try {
                SecurityContextHolder.setContext(context);
                return supplier.get();
            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
        }, IO_EXECUTOR);
    }

    private <T> T join(CompletableFuture<T> future, T fallback) {
        try {
            T value = future.join();
            return value == null ? fallback : value;
        } catch (CompletionException exception) {
            return fallback;
        }
    }

    private Map<String, Object> emptyKpis() {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("taskTotal", kpi(null, null));
        kpis.put("patrolMileage", kpi(null, null));
        kpis.put("aiAlarmTotal", kpi(null, null));
        kpis.put("autoHandleSuccessRate", kpi(null, null));
        return kpis;
    }

    private Map<String, Object> emptyEquipmentRuntime() {
        return object(
                "onlineRate", null,
                "taskCompletionRate", null,
                "unit", null,
                "items", List.of());
    }

    private Map<String, Object> emptyAiAlarmAnalysis() {
        return object(
                "alarmTypeRanking", List.of(),
                "handleMethodRanking", List.of());
    }

    private Map<String, Object> emptyAlarmTrend() {
        return object(
                "unit", null,
                "points", List.of());
    }

    private Map<String, Object> emptyTaskCompletion() {
        return object(
                "items", List.of(),
                "insight", null);
    }

    private ReportSelection reportSelection(Map<String, Object> request) {
        Map<String, Object> timeRange = mapValue(request.get("timeRange"));
        String rangeType = stringValue(timeRange.get("type"), stringValue(request.get("range"), "month"));
        String startTime = stringValue(timeRange.get("startTime"), null);
        String endTime = stringValue(timeRange.get("endTime"), null);
        String deviceType = stringValue(request.get("deviceType"), "all");
        List<String> modules = stringList(request.get("modules"));
        if (modules.isEmpty()) {
            modules = List.of("equipmentRuntime", "aiAlarmAnalysis", "alarmAreaRanking", "alarmTrend", "taskCompletion");
        }
        return new ReportSelection(rangeType, startTime, endTime, deviceType, new LinkedHashSet<>(modules));
    }

    private byte[] reportPdf(
            Map<String, Object> data,
            ReportSelection selection,
            List<Map<String, Object>> deviceTypeOptions) {
        PdfReportBuilder pdf = new PdfReportBuilder();
        Map<String, Object> range = mapValue(data.get("range"));
        Map<String, Object> kpis = mapValue(data.get("kpis"));

        pdf.title("具身智能平台统计报告", "巡逻巡查数据统计分析");
        int sectionNumber = 1;
        pdf.section(sectionTitle(sectionNumber++, "报告概况"));
        pdf.line("生成时间：" + valueText(data.get("serverTime")));
        pdf.line("统计周期：" + rangeLabel(stringValue(range.get("type"), selection.rangeType())));
        pdf.line("起止时间：" + rangeText(range.get("startTime"), range.get("endTime")));
        pdf.line("设备类型：" + valueText(deviceTypeName(selection.deviceType(), deviceTypeOptions)));
        pdf.line("统计内容：" + valueText(moduleNames(selection.modules())));

        pdf.section(sectionTitle(sectionNumber++, "核心指标"));
        pdf.line("任务执行总数：" + valueWithUnit(kpiValue(kpis, "taskTotal"), "个") + "，"
                + compareText(kpis, "taskTotal"));
        pdf.line("总巡逻里程：" + valueWithUnit(kpiValue(kpis, "patrolMileage"), "KM") + "，"
                + compareText(kpis, "patrolMileage"));
        pdf.line("AI自动识别异常数：" + valueWithUnit(kpiValue(kpis, "aiAlarmTotal"), "个") + "，"
                + compareText(kpis, "aiAlarmTotal"));
        pdf.line("自动处置成功率：" + percentText(kpiValue(kpis, "autoHandleSuccessRate")) + "，"
                + compareText(kpis, "autoHandleSuccessRate"));

        if (selection.modules().contains("equipmentRuntime")) {
            Map<String, Object> runtime = mapValue(data.get("equipmentRuntime"));
            pdf.section(sectionTitle(sectionNumber++, "装备运行时长"));
            pdf.line("总在线率：" + percentText(runtime.get("onlineRate"))
                    + "    任务完成率：" + percentText(runtime.get("taskCompletionRate")));
            pdf.tableHeader("设备类型", "运行中(小时)", "故障(小时)", "离线(小时)");
            List<Map<String, Object>> items = mapList(runtime.get("items"));
            for (Map<String, Object> item : items) {
                pdf.tableRow(
                        valueText(item.get("deviceTypeName")),
                        valueText(item.get("runningHours")),
                        valueText(item.get("faultHours")),
                        valueText(item.get("offlineHours")));
            }
            if (items.isEmpty()) {
                pdf.tableEmpty();
            }
        }

        if (selection.modules().contains("aiAlarmAnalysis")) {
            Map<String, Object> ai = mapValue(data.get("aiAlarmAnalysis"));
            pdf.section(sectionTitle(sectionNumber++, "AI告警分析"));
            pdf.line("告警类型分布：");
            List<Map<String, Object>> alarmTypes = mapList(ai.get("alarmTypeRanking"));
            for (Map<String, Object> item : alarmTypes) {
                pdf.line("  " + valueText(item.get("name")) + "："
                        + valueWithUnit(item.get("count"), "次") + "，占比 " + percentText(item.get("percent")));
            }
            if (alarmTypes.isEmpty()) {
                pdf.emptyData();
            }
            pdf.line("处理方式分布：");
            List<Map<String, Object>> handleMethods = mapList(ai.get("handleMethodRanking"));
            for (Map<String, Object> item : handleMethods) {
                pdf.line("  " + valueText(item.get("name")) + "：" + valueWithUnit(item.get("count"), "次"));
            }
            if (handleMethods.isEmpty()) {
                pdf.emptyData();
            }
        }

        if (selection.modules().contains("alarmAreaRanking")) {
            pdf.section(sectionTitle(sectionNumber++, "告警高发区域"));
            pdf.tableHeader("排名", "区域", "告警次数", "占比");
            int rank = 1;
            List<Map<String, Object>> areaRanking = mapList(data.get("alarmAreaRanking"));
            for (Map<String, Object> item : areaRanking) {
                pdf.tableRow(
                        "TOP." + rank++,
                        valueText(item.get("areaName")),
                        valueText(item.get("count")),
                        percentText(item.get("percent")));
            }
            if (areaRanking.isEmpty()) {
                pdf.tableEmpty();
            }
        }

        if (selection.modules().contains("alarmTrend")) {
            Map<String, Object> trend = mapValue(data.get("alarmTrend"));
            pdf.section(sectionTitle(sectionNumber++, "告警异常趋势"));
            List<String> points = new ArrayList<>();
            for (Map<String, Object> item : mapList(trend.get("points"))) {
                points.add(valueText(item.get("label")) + "："
                        + valueWithUnit(item.get("count"), valueTextOrEmpty(trend.get("unit"))));
            }
            if (points.isEmpty()) {
                pdf.emptyData();
            } else {
                pdf.line(String.join("    ", points));
            }
        }

        if (selection.modules().contains("taskCompletion")) {
            Map<String, Object> completion = mapValue(data.get("taskCompletion"));
            pdf.keepTogether(145);
            pdf.section(sectionTitle(sectionNumber++, "任务完成情况"));
            List<Map<String, Object>> items = mapList(completion.get("items"));
            for (Map<String, Object> item : items) {
                pdf.line(valueText(item.get("name")) + "：" + valueWithUnit(item.get("count"), "个")
                        + "，占比 " + percentText(item.get("percent")));
            }
            if (items.isEmpty()) {
                pdf.emptyData();
            }
            pdf.line("统计结论：" + valueText(completion.get("insight")));
        }

        pdf.section(sectionTitle(sectionNumber, "报告说明"));
        pdf.line("本报告由具身智能平台依据所选统计周期、设备类型及统计内容自动汇总生成，"
                + "用于反映平台装备运行、任务执行和告警处置情况。");
        pdf.line("报告数据以生成时平台已接入并可查询的数据为准；暂无数据的指标统一标记为“暂无数据”。");
        pdf.line("本报告用于运行态势分析和管理决策参考，具体业务结论应结合现场记录及相关业务系统复核。");
        return pdf.build();
    }

    private Object kpiValue(Map<String, Object> kpis, String code) {
        return mapValue(kpis.get(code)).get("value");
    }

    private String compareText(Map<String, Object> kpis, String code) {
        Object rate = mapValue(kpis.get(code)).get("compareRate");
        if (!(rate instanceof Number number)) {
            return "暂无环比数据";
        }
        String prefix = number.doubleValue() > 0 ? "+" : "";
        return "环比 " + prefix + number + "%";
    }

    private String rangeLabel(String range) {
        return switch (range) {
            case "today" -> "今日";
            case "week" -> "本周";
            case "month" -> "本月";
            case "all" -> "全部";
            case "custom" -> "自定义";
            default -> range;
        };
    }

    private List<Map<String, Object>> deviceTypeOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Map<String, Object> source : centerClient.deviceTypeOptions()) {
            String value = firstString(source, "value", "itemValue", "itemCode", "code");
            String label = firstString(source, "label", "itemName", "name");
            if (value != null && label != null) {
                options.put(value, label);
            }
        }
        options.putIfAbsent("FIXED_CAMERA", "固定摄像头");
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(object("value", "all", "label", "全部"));
        options.forEach((value, label) -> result.add(object("value", value, "label", label)));
        return result;
    }

    private String deviceTypeName(String deviceType, List<Map<String, Object>> options) {
        return options.stream()
                .filter(option -> deviceType.equals(stringValue(option.get("value"), null)))
                .map(option -> stringValue(option.get("label"), deviceType))
                .findFirst()
                .orElse(deviceType);
    }

    private String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = stringValue(source.get(key), null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String moduleNames(Set<String> modules) {
        List<String> names = new ArrayList<>();
        for (String module : modules) {
            names.add(switch (module) {
                case "equipmentRuntime" -> "装备运行时长";
                case "aiAlarmAnalysis" -> "AI告警分析";
                case "alarmAreaRanking" -> "告警高发区域";
                case "alarmTrend" -> "告警趋势图";
                case "taskCompletion" -> "任务完成率";
                default -> module;
            });
        }
        return String.join("、", names);
    }

    private String valueText(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? "暂无数据" : text;
    }

    private String valueTextOrEmpty(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private String valueWithUnit(Object value, String unit) {
        String text = valueText(value);
        return "暂无数据".equals(text) ? text : text + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    private String percentText(Object value) {
        return valueWithUnit(value, "%").replace(" %", "%");
    }

    private String rangeText(Object startTime, Object endTime) {
        String start = valueText(startTime);
        String end = valueText(endTime);
        if ("暂无数据".equals(start) && "暂无数据".equals(end)) {
            return "全部可用数据";
        }
        return start + " 至 " + end;
    }

    private String sectionTitle(int number, String title) {
        String[] numbers = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        String label = number >= 0 && number < numbers.length ? numbers[number] : String.valueOf(number);
        return label + "、" + title;
    }

    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    list.add(String.valueOf(item));
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }

    private Map<String, Object> kpi(Object value, Number compareRate) {
        return object(
                "value", value,
                "compareRate", compareRate);
    }

    private String now() {
        return LocalDateTime.now(CHINA_ZONE).format(DATE_TIME_FORMATTER);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Map<String, Object> object(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }

    private record ReportSelection(String rangeType, String startTime, String endTime, String deviceType,
            Set<String> modules) {
    }

    public record ReportFile(String id, String filename, byte[] bytes) {
    }

    private record MileageWindow(
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime previousStart,
            LocalDateTime previousEnd) {
    }

    private record ReportHistory(String id, String reportName, String filename, String downloadTime, String format,
            String status, String filePath) {

        Map<String, Object> toResponse() {
            return object(
                    "id", id,
                    "reportName", reportName,
                    "downloadTime", downloadTime,
                    "format", format,
                    "status", status,
                    "filename", filename,
                    "filePath", filePath,
                    // 兼容当前历史报告弹窗的占位字段，前端后续可直接切换到上面的标准字段。
                    "statusName", "COMPLETED".equals(status) ? "已完成" : status);
        }

        Map<String, Object> toIndexEntry() {
            return object(
                    "id", id,
                    "reportName", reportName,
                    "filename", filename,
                    "downloadTime", downloadTime,
                    "format", format,
                    "status", status,
                    "filePath", filePath);
        }

        private Map<String, Object> object(Object... entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < entries.length; i += 2) {
                map.put((String) entries[i], entries[i + 1]);
            }
            return map;
        }
    }

    private static class PdfReportBuilder {

        private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float MARGIN_X = 54;
        private static final float MARGIN_BOTTOM = 58;
        private static final float START_Y = 784;
        private static final float TITLE_SIZE = 24;
        private static final float SUBTITLE_SIZE = 12;
        private static final float SECTION_SIZE = 14;
        private static final float BODY_SIZE = 10;
        private static final float TABLE_SIZE = 9;
        private static final float BODY_STEP = 17;
        private static final Color TEXT_COLOR = new Color(36, 48, 60);
        private static final Color PRIMARY_COLOR = new Color(31, 91, 140);
        private static final Color BORDER_COLOR = new Color(205, 216, 226);
        private static final Color HEADER_BACKGROUND = new Color(237, 245, 250);

        private final PDDocument document = new PDDocument();
        private final List<PDPage> pages = new ArrayList<>();
        private final PDFont font;
        private PDPageContentStream content;
        private float y;

        PdfReportBuilder() {
            try (InputStream input = StatisticsService.class.getResourceAsStream("/fonts/alibaba-puhuiti.ttf")) {
                if (input == null) {
                    throw new IllegalStateException("统计报告字体资源不存在");
                }
                font = PDType0Font.load(document, input, true);
                newPage();
            } catch (IOException exception) {
                closeDocument();
                throw new IllegalStateException("初始化统计报告失败", exception);
            }
        }

        void title(String text, String subtitle) {
            ensureSpace(72);
            addCenteredText(text, TITLE_SIZE, y);
            y -= 28;
            addCenteredText(subtitle, SUBTITLE_SIZE, y);
            y -= 22;
            addLineShape(MARGIN_X, y + 8, PAGE_WIDTH - MARGIN_X * 2, PRIMARY_COLOR);
            y -= 12;
        }

        void section(String text) {
            ensureSpace(36);
            y -= 7;
            addText(text, SECTION_SIZE, MARGIN_X, y);
            y -= 23;
        }

        void line(String text) {
            for (String line : wrap(text, PAGE_WIDTH - MARGIN_X * 2, BODY_SIZE)) {
                ensureSpace(BODY_STEP);
                addText(line, BODY_SIZE, MARGIN_X, y);
                y -= BODY_STEP;
            }
        }

        void keepTogether(float height) {
            ensureSpace(height);
        }

        void emptyData() {
            line("  暂无数据");
        }

        void tableHeader(String... columns) {
            ensureSpace(24);
            addFilledRect(MARGIN_X, y - 7, PAGE_WIDTH - MARGIN_X * 2, 20, HEADER_BACKGROUND);
            addTableColumns(columns, TABLE_SIZE, y);
            y -= 22;
        }

        void tableRow(String... columns) {
            ensureSpace(22);
            addTableColumns(columns, TABLE_SIZE, y);
            addLineShape(MARGIN_X, y - 7, PAGE_WIDTH - MARGIN_X * 2, BORDER_COLOR);
            y -= 22;
        }

        void tableEmpty() {
            ensureSpace(22);
            addCenteredText("暂无数据", TABLE_SIZE, y);
            addLineShape(MARGIN_X, y - 7, PAGE_WIDTH - MARGIN_X * 2, BORDER_COLOR);
            y -= 22;
        }

        byte[] build() {
            try {
                closeContent();
                int pageCount = pages.size();
                for (int i = 0; i < pageCount; i++) {
                    try (PDPageContentStream footer = new PDPageContentStream(
                            document,
                            pages.get(i),
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true)) {
                        String text = "具身智能平台统计报告    第 " + (i + 1) + " / " + pageCount + " 页";
                        showText(footer, text, 8, centeredX(text, 8), 34, new Color(102, 112, 122));
                    }
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                document.save(output);
                return output.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException("生成统计报告失败", exception);
            } finally {
                closeDocument();
            }
        }

        private void newPage() {
            try {
                closeContent();
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                pages.add(page);
                content = new PDPageContentStream(document, page);
                y = START_Y;
                if (pages.size() > 1) {
                    addText("具身智能平台统计报告", 9, MARGIN_X, 812);
                    addLineShape(MARGIN_X, 802, PAGE_WIDTH - MARGIN_X * 2, BORDER_COLOR);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("创建统计报告页面失败", exception);
            }
        }

        private void ensureSpace(float height) {
            if (y - height < MARGIN_BOTTOM) {
                newPage();
            }
        }

        private void addText(String text, float size, float x, float baseline) {
            try {
                showText(content, valueOf(text), size, x, baseline, TEXT_COLOR);
            } catch (IOException exception) {
                throw new IllegalStateException("写入统计报告文字失败", exception);
            }
        }

        private void addCenteredText(String text, float size, float baseline) {
            addText(text, size, centeredX(text, size), baseline);
        }

        private void addTableColumns(String[] columns, float size, float baseline) {
            float width = PAGE_WIDTH - MARGIN_X * 2;
            float columnWidth = width / Math.max(columns.length, 1);
            for (int i = 0; i < columns.length; i++) {
                addText(fitText(columns[i], columnWidth - 10, size), size, MARGIN_X + i * columnWidth + 5, baseline);
            }
        }

        private void addLineShape(float x, float lineY, float width, Color color) {
            try {
                content.setStrokingColor(color);
                content.setLineWidth(0.6f);
                content.moveTo(x, lineY);
                content.lineTo(x + width, lineY);
                content.stroke();
            } catch (IOException exception) {
                throw new IllegalStateException("绘制统计报告分隔线失败", exception);
            }
        }

        private void addFilledRect(float x, float rectY, float width, float height, Color color) {
            try {
                content.setNonStrokingColor(color);
                content.addRect(x, rectY, width, height);
                content.fill();
            } catch (IOException exception) {
                throw new IllegalStateException("绘制统计报告表格失败", exception);
            }
        }

        private List<String> wrap(String text, float maxWidth, float size) {
            List<String> lines = new ArrayList<>();
            String source = valueOf(text);
            StringBuilder current = new StringBuilder();
            for (String token : source.split(" ", -1)) {
                String candidate = current.isEmpty() ? token : current + " " + token;
                if (textWidth(candidate, size) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                appendWrappedToken(lines, current, token, maxWidth, size);
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private void appendWrappedToken(
                List<String> lines,
                StringBuilder current,
                String token,
                float maxWidth,
                float size) {
            for (int i = 0; i < token.length(); i++) {
                String candidate = current.toString() + token.charAt(i);
                if (!current.isEmpty() && textWidth(candidate, size) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(token.charAt(i));
            }
        }

        private float textWidth(String text, float size) {
            try {
                return font.getStringWidth(valueOf(text)) / 1000 * size;
            } catch (IOException exception) {
                throw new IllegalStateException("计算统计报告文字宽度失败", exception);
            }
        }

        private String fitText(String text, float maxWidth, float size) {
            String value = valueOf(text);
            try {
                if (font.getStringWidth(value) / 1000 * size <= maxWidth) {
                    return value;
                }
                String suffix = "…";
                while (!value.isEmpty()
                        && font.getStringWidth(value + suffix) / 1000 * size > maxWidth) {
                    value = value.substring(0, value.length() - 1);
                }
                return value + suffix;
            } catch (IOException exception) {
                throw new IllegalStateException("计算统计报告文字宽度失败", exception);
            }
        }

        private float centeredX(String text, float size) {
            try {
                float width = font.getStringWidth(valueOf(text)) / 1000 * size;
                return Math.max(MARGIN_X, (PAGE_WIDTH - width) / 2);
            } catch (IOException exception) {
                throw new IllegalStateException("计算统计报告标题位置失败", exception);
            }
        }

        private void showText(
                PDPageContentStream stream,
                String text,
                float size,
                float x,
                float baseline,
                Color color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(valueOf(text));
            stream.endText();
        }

        private void closeContent() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }

        private void closeDocument() {
            try {
                if (content != null) {
                    content.close();
                    content = null;
                }
                document.close();
            } catch (IOException ignored) {
                // 主异常优先，关闭失败不覆盖生成错误。
            }
        }

        private static String valueOf(String text) {
            return text == null ? "" : text;
        }
    }

}
