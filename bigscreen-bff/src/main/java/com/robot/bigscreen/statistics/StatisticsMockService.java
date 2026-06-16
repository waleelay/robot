package com.robot.bigscreen.statistics;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StatisticsMockService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        String text = buildReportText(request);
        return simplePdf(text);
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

    private String buildReportText(Map<String, Object> request) {
        Object modules = request.getOrDefault("modules", List.of());
        Object timeRange = request.getOrDefault("timeRange", Map.of("type", "month"));
        Object deviceType = request.getOrDefault("deviceType", "all");
        return """
                Patrol Statistics Report
                Generated At: %s
                Modules: %s
                Time Range: %s
                Device Type: %s

                KPI Summary
                - Task Total: 288
                - Patrol Mileage: 356.8 KM
                - AI Alarm Total: 286
                - Auto Handle Success Rate: 100%%

                This is a synchronous mock report stream generated by Bigscreen BFF.
                """.formatted(now(), modules, timeRange, deviceType);
    }

    private byte[] simplePdf(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", "");
        String[] lines = escaped.split("\n");
        StringBuilder content = new StringBuilder("BT\n/F1 12 Tf\n50 780 Td\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                content.append("0 -18 Td\n");
            }
            content.append("(").append(lines[i]).append(") Tj\n");
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.length + " >>\nstream\n" + content + "endstream");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
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
