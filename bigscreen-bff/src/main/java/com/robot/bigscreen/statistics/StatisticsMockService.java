package com.robot.bigscreen.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatisticsMockService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicLong reportId = new AtomicLong();
    private final Map<String, ReportHistory> reportHistories = new ConcurrentSkipListMap<>();
    private final Object historyLock = new Object();
    private final ObjectMapper objectMapper;
    private final Path reportStorageDir;
    private final Path reportIndexPath;

    public StatisticsMockService(
            ObjectMapper objectMapper,
            @Value("${statistics.report.storage-dir:data/statistics-reports}") String reportStorageDir) {
        this.objectMapper = objectMapper;
        this.reportStorageDir = Paths.get(reportStorageDir);
        this.reportIndexPath = this.reportStorageDir.resolve("index.json");
        loadReportHistories();
    }

    public Map<String, Object> overview(String range, String startTime, String endTime, String deviceType, String areaId) {
        Map<String, Object> normalizedRange = normalizedRange(range, startTime, endTime);
        StatsScenario scenario = scenario((String) normalizedRange.get("type"), blankToDefault(deviceType, "all"));
        return object(
                "serverTime", now(),
                "range", normalizedRange,
                "filters", object(
                        "deviceType", blankToDefault(deviceType, "all"),
                        "areaId", areaId),
                "kpis", kpis(scenario),
                "equipmentRuntime", equipmentRuntime(scenario),
                "aiAlarmAnalysis", aiAlarmAnalysis(scenario),
                "alarmAreaRanking", alarmAreaRanking(scenario),
                "alarmTrend", alarmTrend(scenario),
                "taskCompletion", taskCompletion(scenario));
    }

    public byte[] exportPdf(Map<String, Object> request) {
        return createReport(request).bytes();
    }

    public ReportFile createReport(Map<String, Object> request) {
        ReportSelection selection = reportSelection(request);
        Map<String, Object> data = overview(
                selection.rangeType(),
                selection.startTime(),
                selection.endTime(),
                selection.deviceType(),
                null);
        byte[] bytes = reportPdf(data, selection);
        String id = String.valueOf(reportId.incrementAndGet());
        String createdAt = now();
        String reportName = "巡逻巡查数据统计报告-" + rangeLabel(selection.rangeType()) + "-" + deviceTypeName(selection.deviceType());
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
                        stringValue(row.get("reportName"), "巡逻巡查数据统计报告"),
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

    private Map<String, Object> kpis(StatsScenario scenario) {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("taskTotal", kpi(scaleInt(288, scenario), scenario.taskCompare()));
        kpis.put("patrolMileage", kpi(scaleDouble(356.8, scenario), scenario.mileageCompare()));
        kpis.put("aiAlarmTotal", kpi(scaleInt(286, scenario), scenario.alarmCompare()));
        kpis.put("autoHandleSuccessRate", kpi(scenario.successRate(), scenario.successCompare()));
        return kpis;
    }

    private Map<String, Object> equipmentRuntime(StatsScenario scenario) {
        return object(
                "onlineRate", scenario.onlineRate(),
                "taskCompletionRate", scenario.taskCompletionRate(),
                "unit", "小时",
                "items", List.of(
                        runtime("UAV", "无人机", scenario, 72, 6, 21),
                        runtime("ROBOT_DOG", "机器狗", scenario, 88, 14, 18),
                        runtime("UGV", "无人车", scenario, 96, 10, 16),
                        runtime("HUMANOID_ROBOT", "机器人", scenario, 64, 8, 12)));
    }

    private Map<String, Object> aiAlarmAnalysis(StatsScenario scenario) {
        return object(
                "alarmTypeRanking", List.of(
                        alarmType("FIGHT", "打架斗殴", scaleInt(80, scenario), scenario.percent(28.0, 0)),
                        alarmType("CLIMB_FENCE", "攀爬围栏", scaleInt(66, scenario), scenario.percent(23.1, 1)),
                        alarmType("PERSON_ALONE", "人员落单", scaleInt(58, scenario), scenario.percent(20.3, 2)),
                        alarmType("PERSON_GATHER", "人员聚集", scaleInt(50, scenario), scenario.percent(17.5, 3)),
                        alarmType("OUTSOURCER_ESCORT", "外协陪同", scaleInt(32, scenario), scenario.percent(11.1, 4))),
                "handleMethodRanking", List.of(
                        handleMethod("VOICE_BROADCAST", "语音播报", scaleInt(96, scenario)),
                        handleMethod("AUTO_DISPATCH", "自动调度", scaleInt(58, scenario)),
                        handleMethod("REMOTE_CONTROL", "远程控制", scaleInt(39, scenario)),
                        handleMethod("MANUAL_HANDLE", "人工处理", scaleInt(17, scenario))));
    }

    private List<Map<String, Object>> alarmAreaRanking(StatsScenario scenario) {
        int top = scaleInt(52, scenario);
        return List.of(
                area("area-001", "2监区8号楼", top, 100),
                rankedArea("area-002", "训练场西门", scaleInt(20, scenario), top),
                rankedArea("area-003", "食堂大门", scaleInt(18, scenario), top),
                rankedArea("area-004", "3监区2号楼", scaleInt(10, scenario), top),
                rankedArea("area-005", "训练场东门", scaleInt(6, scenario), top));
    }

    private Map<String, Object> alarmTrend(StatsScenario scenario) {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        List<Map<String, Object>> points = new ArrayList<>();
        int[] values = scenario.trendValues();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            points.add(object(
                    "date", date.format(DATE_FORMATTER),
                    "label", date.getMonthValue() + "." + date.getDayOfMonth(),
                    "count", values[6 - i]));
        }
        return object(
                "unit", "次",
                "points", points);
    }

    private Map<String, Object> taskCompletion(StatsScenario scenario) {
        return object(
                "items", List.of(
                        taskStatus("COMPLETED", "已完成", scaleInt(196, scenario), scenario.completedRate()),
                        taskStatus("RUNNING", "执行中", scaleInt(124, scenario), scenario.runningRate()),
                        taskStatus("INTERRUPTED", "异常中断", scaleInt(20, scenario), scenario.interruptedRate())),
                "insight", scenario.insight());
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

    private byte[] reportPdf(Map<String, Object> data, ReportSelection selection) {
        PdfReportBuilder pdf = new PdfReportBuilder();
        Map<String, Object> range = mapValue(data.get("range"));
        Map<String, Object> kpis = mapValue(data.get("kpis"));

        pdf.title("巡逻巡查数据统计报告");
        pdf.line("生成时间：" + data.get("serverTime"));
        pdf.line("统计时间：" + rangeLabel(stringValue(range.get("type"), selection.rangeType())) + "（"
                + valueText(range.get("startTime")) + " 至 " + valueText(range.get("endTime")) + "）");
        pdf.line("设备类型：" + deviceTypeName(selection.deviceType()));
        pdf.line("包含模块：" + moduleNames(selection.modules()));

        pdf.section("一、核心指标");
        pdf.line("任务执行总数：" + kpiValue(kpis, "taskTotal") + " 个，" + compareText(kpis, "taskTotal"));
        pdf.line("总巡逻里程：" + kpiValue(kpis, "patrolMileage") + " KM，" + compareText(kpis, "patrolMileage"));
        pdf.line("AI自动识别异常数：" + kpiValue(kpis, "aiAlarmTotal") + " 个，" + compareText(kpis, "aiAlarmTotal"));
        pdf.line("自动处置成功率：" + kpiValue(kpis, "autoHandleSuccessRate") + "%，"
                + compareText(kpis, "autoHandleSuccessRate"));

        if (selection.modules().contains("equipmentRuntime")) {
            Map<String, Object> runtime = mapValue(data.get("equipmentRuntime"));
            pdf.section("二、装备运行时长");
            pdf.line("总在线率：" + runtime.get("onlineRate") + "%    任务完成率：" + runtime.get("taskCompletionRate") + "%");
            pdf.tableHeader("设备类型", "运行中(小时)", "故障(小时)", "离线(小时)");
            for (Map<String, Object> item : mapList(runtime.get("items"))) {
                pdf.tableRow(
                        valueText(item.get("deviceTypeName")),
                        valueText(item.get("runningHours")),
                        valueText(item.get("faultHours")),
                        valueText(item.get("offlineHours")));
            }
        }

        if (selection.modules().contains("aiAlarmAnalysis")) {
            Map<String, Object> ai = mapValue(data.get("aiAlarmAnalysis"));
            pdf.section("三、AI告警分析");
            pdf.line("告警类型分布：");
            for (Map<String, Object> item : mapList(ai.get("alarmTypeRanking"))) {
                pdf.line("  " + item.get("name") + "：" + item.get("count") + " 次，占比 " + item.get("percent") + "%");
            }
            pdf.line("处理方式分布：");
            for (Map<String, Object> item : mapList(ai.get("handleMethodRanking"))) {
                pdf.line("  " + item.get("name") + "：" + item.get("count") + " 次");
            }
        }

        if (selection.modules().contains("alarmAreaRanking")) {
            pdf.section("四、告警高发区域");
            pdf.tableHeader("排名", "区域", "告警次数", "占比");
            int rank = 1;
            for (Map<String, Object> item : mapList(data.get("alarmAreaRanking"))) {
                pdf.tableRow(
                        "TOP." + rank++,
                        valueText(item.get("areaName")),
                        valueText(item.get("count")),
                        valueText(item.get("percent")) + "%");
            }
        }

        if (selection.modules().contains("alarmTrend")) {
            Map<String, Object> trend = mapValue(data.get("alarmTrend"));
            pdf.section("五、告警异常趋势");
            List<String> points = new ArrayList<>();
            for (Map<String, Object> item : mapList(trend.get("points"))) {
                points.add(item.get("label") + "：" + item.get("count") + trend.get("unit"));
            }
            pdf.line(String.join("    ", points));
        }

        if (selection.modules().contains("taskCompletion")) {
            Map<String, Object> completion = mapValue(data.get("taskCompletion"));
            pdf.section("六、任务完成情况");
            for (Map<String, Object> item : mapList(completion.get("items"))) {
                pdf.line(item.get("name") + "：" + item.get("count") + " 个，" + item.get("percent") + "%");
            }
            pdf.line("统计结论：" + valueText(completion.get("insight")));
        }

        pdf.section("七、报告说明");
        pdf.line("本报告由数据统计大屏按筛选条件同步生成，当前为模拟数据版本，用于接口联调和报告模板确认。");
        return pdf.build();
    }

    private Object kpiValue(Map<String, Object> kpis, String code) {
        return mapValue(kpis.get(code)).getOrDefault("value", 0);
    }

    private String compareText(Map<String, Object> kpis, String code) {
        Object rate = mapValue(kpis.get(code)).get("compareRate");
        if (!(rate instanceof Number number)) {
            return "环比 -";
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

    private String deviceTypeName(String deviceType) {
        return switch (deviceType) {
            case "UAV" -> "无人机";
            case "ROBOT_DOG" -> "机器狗";
            case "UGV" -> "无人车";
            case "HUMANOID_ROBOT" -> "机器人";
            default -> "全部";
        };
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
        return value == null ? "-" : String.valueOf(value);
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

    private Map<String, Object> runtime(String deviceType, String deviceTypeName, StatsScenario scenario,
            int runningHours, int faultHours, int offlineHours) {
        double typeBoost = scenario.deviceType().equals(deviceType) ? 1.45 : scenario.deviceType().equals("all") ? 1 : 0.55;
        return object(
                "deviceType", deviceType,
                "deviceTypeName", deviceTypeName,
                "runningHours", Math.max(1, (int) Math.round(runningHours * scenario.rangeFactor() * typeBoost)),
                "faultHours", Math.max(0, (int) Math.round(faultHours * scenario.rangeFactor() * typeBoost)),
                "offlineHours", Math.max(0, (int) Math.round(offlineHours * scenario.rangeFactor() * typeBoost)));
    }

    private Map<String, Object> alarmType(String type, String name, int count, double percent) {
        return object("type", type, "name", name, "count", count, "percent", percent);
    }

    private Map<String, Object> handleMethod(String method, String name, int count) {
        return object("method", method, "name", name, "count", count);
    }

    private Map<String, Object> area(String areaId, String areaName, int count, double percent) {
        return object("areaId", areaId, "areaName", areaName, "count", count, "percent", percent);
    }

    private Map<String, Object> rankedArea(String areaId, String areaName, int count, int top) {
        return area(areaId, areaName, count, Math.round(count * 1000.0 / Math.max(top, 1)) / 10.0);
    }

    private Map<String, Object> taskStatus(String status, String name, int count, double percent) {
        return object("status", status, "name", name, "count", count, "percent", percent);
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

    private StatsScenario scenario(String range, String deviceType) {
        return new StatsScenario(range, deviceType);
    }

    private int scaleInt(int value, StatsScenario scenario) {
        return Math.max(0, (int) Math.round(value * scenario.rangeFactor() * scenario.deviceFactor()));
    }

    private double scaleDouble(double value, StatsScenario scenario) {
        return Math.round(value * scenario.rangeFactor() * scenario.deviceFactor() * 10.0) / 10.0;
    }

    private record ReportSelection(String rangeType, String startTime, String endTime, String deviceType,
            Set<String> modules) {
    }

    public record ReportFile(String id, String filename, byte[] bytes) {
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
                    "mapName", reportName,
                    "equipmentName", downloadTime,
                    "jobName", format,
                    "alertTimes", "COMPLETED".equals(status) ? "已完成" : status);
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

        private static final int PAGE_WIDTH = 595;
        private static final int PAGE_HEIGHT = 842;
        private static final int MARGIN_X = 48;
        private static final int MARGIN_BOTTOM = 52;
        private static final int START_Y = 790;
        private static final int BODY_SIZE = 11;
        private static final int BODY_STEP = 18;

        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder content;
        private int y;

        PdfReportBuilder() {
            newPage();
        }

        void title(String text) {
            ensureSpace(40);
            addText(text, 20, MARGIN_X, y);
            y -= 30;
            addLineShape(MARGIN_X, y + 10, PAGE_WIDTH - MARGIN_X * 2);
            y -= 12;
        }

        void section(String text) {
            y -= 6;
            ensureSpace(34);
            addText(text, 14, MARGIN_X, y);
            y -= 22;
        }

        void line(String text) {
            for (String line : wrap(text, 44)) {
                ensureSpace(BODY_STEP);
                addText(line, BODY_SIZE, MARGIN_X, y);
                y -= BODY_STEP;
            }
        }

        void tableHeader(String... columns) {
            ensureSpace(BODY_STEP);
            addText(String.join("        ", columns), BODY_SIZE, MARGIN_X, y);
            y -= BODY_STEP;
        }

        void tableRow(String... columns) {
            line(String.join("        ", columns));
        }

        byte[] build() {
            List<String> objects = new ArrayList<>();
            int pageCount = pages.size();
            int firstPageObj = 3;
            int fontObj = firstPageObj + pageCount * 2;
            int cidFontObj = fontObj + 1;
            objects.add("<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pageCount; i++) {
                kids.append(firstPageObj + i * 2).append(" 0 R ");
            }
            objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
            for (int i = 0; i < pageCount; i++) {
                int pageObj = firstPageObj + i * 2;
                int contentObj = pageObj + 1;
                String stream = pages.get(i).toString();
                int length = stream.getBytes(StandardCharsets.ISO_8859_1).length;
                objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT
                        + "] /Resources << /Font << /F1 " + fontObj + " 0 R >> >> /Contents " + contentObj
                        + " 0 R >>");
                objects.add("<< /Length " + length + " >>\nstream\n" + stream + "endstream");
            }
            objects.add("<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H "
                    + "/DescendantFonts [" + cidFontObj + " 0 R] >>");
            objects.add("<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light "
                    + "/CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 2 >> >>");
            return serialize(objects);
        }

        private void newPage() {
            content = new StringBuilder();
            pages.add(content);
            y = START_Y;
        }

        private void ensureSpace(int height) {
            if (y - height < MARGIN_BOTTOM) {
                newPage();
            }
        }

        private void addText(String text, int size, int x, int baseline) {
            content.append("BT\n/F1 ").append(size).append(" Tf\n1 0 0 1 ")
                    .append(x).append(" ").append(baseline).append(" Tm\n<")
                    .append(toUtf16Hex(text)).append("> Tj\nET\n");
        }

        private void addLineShape(int x, int y, int width) {
            content.append("0.1 0.45 0.75 RG\n")
                    .append(x).append(" ").append(y).append(" m ")
                    .append(x + width).append(" ").append(y).append(" l S\n");
        }

        private List<String> wrap(String text, int maxChars) {
            List<String> lines = new ArrayList<>();
            String source = valueOf(text);
            for (int start = 0; start < source.length(); start += maxChars) {
                lines.add(source.substring(start, Math.min(start + maxChars, source.length())));
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private static String valueOf(String text) {
            return text == null ? "" : text;
        }

        private static String toUtf16Hex(String text) {
            byte[] bytes = valueOf(text).getBytes(StandardCharsets.UTF_16BE);
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02X", b & 0xff));
            }
            return hex.toString();
        }

        private static byte[] serialize(List<String> objects) {
            StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
            }
            int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
            pdf.append("0000000000 65535 f \n");
            for (Integer offset : offsets) {
                pdf.append(String.format("%010d 00000 n \n", offset));
            }
            pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
            pdf.append("startxref\n").append(xref).append("\n%%EOF\n");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private record StatsScenario(String range, String deviceType) {

        double rangeFactor() {
            return switch (range) {
                case "today" -> 0.18;
                case "week" -> 0.62;
                case "all" -> 2.15;
                default -> 1.0;
            };
        }

        double deviceFactor() {
            return switch (deviceType) {
                case "UAV" -> 0.32;
                case "ROBOT_DOG" -> 0.44;
                case "UGV" -> 0.58;
                case "HUMANOID_ROBOT" -> 0.36;
                default -> 1.0;
            };
        }

        String compareLabel() {
            return switch (range) {
                case "today" -> "较昨日";
                case "week" -> "较上周";
                case "all" -> "较同期";
                default -> "较上月";
            };
        }

        int taskCompare() {
            return switch (range) {
                case "today" -> 8;
                case "week" -> 12;
                case "all" -> 18;
                default -> -5;
            } + deviceOffset();
        }

        int mileageCompare() {
            return switch (range) {
                case "today" -> 5;
                case "week" -> -3;
                case "all" -> 15;
                default -> -5;
            } + deviceOffset();
        }

        int alarmCompare() {
            return switch (range) {
                case "today" -> -12;
                case "week" -> 9;
                case "all" -> 22;
                default -> -5;
            } + deviceOffset();
        }

        int successCompare() {
            return switch (range) {
                case "today" -> 2;
                case "week" -> 4;
                case "all" -> 7;
                default -> 5;
            };
        }

        String trend(int compareRate) {
            return compareRate >= 0 ? "up" : "down";
        }

        int successRate() {
            return clampRate(switch (range) {
                case "today" -> 96;
                case "week" -> 98;
                case "all" -> 94;
                default -> 100;
            } - Math.max(deviceOffset(), 0));
        }

        int onlineRate() {
            return clampRate(switch (range) {
                case "today" -> 92;
                case "week" -> 95;
                case "all" -> 89;
                default -> 98;
            } - Math.max(deviceOffset(), 0));
        }

        int taskCompletionRate() {
            return clampRate(switch (range) {
                case "today" -> 88;
                case "week" -> 93;
                case "all" -> 91;
                default -> 100;
            } - Math.max(deviceOffset(), 0));
        }

        double percent(double base, int index) {
            double rangeShift = switch (range) {
                case "today" -> 2.8;
                case "week" -> 1.4;
                case "all" -> -1.2;
                default -> 0;
            };
            double deviceShift = deviceType.equals("all") ? 0 : (index % 2 == 0 ? 1.6 : -1.1);
            return Math.max(1, Math.round((base + rangeShift + deviceShift) * 10.0) / 10.0);
        }

        int[] trendValues() {
            int[] month = {27, 28, 48, 81, 32, 77, 44};
            int[] week = {12, 18, 24, 33, 22, 35, 28};
            int[] today = {2, 4, 5, 8, 6, 9, 7};
            int[] all = {58, 70, 92, 118, 86, 132, 104};
            int[] source = switch (range) {
                case "today" -> today;
                case "week" -> week;
                case "all" -> all;
                default -> month;
            };
            int[] values = new int[source.length];
            for (int i = 0; i < source.length; i++) {
                values[i] = Math.max(0, (int) Math.round(source[i] * deviceFactor()));
            }
            return values;
        }

        int completedRate() {
            return clampRate(switch (range) {
                case "today" -> 86;
                case "week" -> 91;
                case "all" -> 88;
                default -> 98;
            });
        }

        int runningRate() {
            return clampRate(switch (range) {
                case "today" -> 42;
                case "week" -> 55;
                case "all" -> 48;
                default -> 62;
            });
        }

        int interruptedRate() {
            return clampRate(switch (range) {
                case "today" -> 14;
                case "week" -> 8;
                case "all" -> 12;
                default -> 10;
            });
        }

        String insight() {
            return switch (range) {
                case "today" -> "今日任务响应较昨日提升，异常中断主要集中在高峰时段";
                case "week" -> "本周巡逻覆盖率提升，AI告警处置效率保持稳定";
                case "all" -> "累计数据看，任务完成率稳定，重点区域告警仍需持续关注";
                default -> "本月对比上月任务处置时长缩短10%，系统响应速度提升";
            };
        }

        private int deviceOffset() {
            return switch (deviceType) {
                case "UAV" -> -2;
                case "ROBOT_DOG" -> 1;
                case "UGV" -> 3;
                case "HUMANOID_ROBOT" -> -1;
                default -> 0;
            };
        }

        private int clampRate(int value) {
            return Math.max(0, Math.min(100, value));
        }
    }
}
