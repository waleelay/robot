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
public class StatisticsService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicLong reportId = new AtomicLong();
    private final Map<String, ReportHistory> reportHistories = new ConcurrentSkipListMap<>();
    private final Object historyLock = new Object();
    private final ObjectMapper objectMapper;
    private final Path reportStorageDir;
    private final Path reportIndexPath;

    public StatisticsService(
            ObjectMapper objectMapper,
            @Value("${statistics.report.storage-dir:data/statistics-reports}") String reportStorageDir) {
        this.objectMapper = objectMapper;
        this.reportStorageDir = Paths.get(reportStorageDir);
        this.reportIndexPath = this.reportStorageDir.resolve("index.json");
        loadReportHistories();
    }

    public Map<String, Object> overview(String range, String startTime, String endTime, String deviceType, String areaId) {
        Map<String, Object> normalizedRange = normalizedRange(range, startTime, endTime);
        return object(
                "serverTime", now(),
                "range", normalizedRange,
                "filters", object(
                        "deviceType", blankToDefault(deviceType, "all"),
                        "areaId", areaId),
                "kpis", emptyKpis(),
                "equipmentRuntime", emptyEquipmentRuntime(),
                "aiAlarmAnalysis", emptyAiAlarmAnalysis(),
                "alarmAreaRanking", List.of(),
                "alarmTrend", emptyAlarmTrend(),
                "taskCompletion", emptyTaskCompletion());
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
        pdf.line("本报告由数据统计大屏按筛选条件同步生成；未接入真实统计来源的模块以空值或空列表展示。");
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

}
