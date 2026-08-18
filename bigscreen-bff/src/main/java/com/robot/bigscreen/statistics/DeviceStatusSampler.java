package com.robot.bigscreen.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.panorama.PanoramaCenterClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后台定时采样 Control Service 设备注册表（在线/离线状态由 {@code eiop/v1/edge/+/status}
 * MQTT 上报维护，心跳超时置离线）。注册表接口免认证，采样不依赖用户 token，
 * 提供真正的无人值守后台采样。按天累计各设备运行/离线/故障计数与首末采样时间戳。
 */
@Component
public class DeviceStatusSampler {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusSampler.class);
    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** serial -> (date -> 当日采样聚合) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, DaySample>> samples = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final PanoramaCenterClient centerClient;
    private final Path storageDir;
    private final int retentionDays;

    public DeviceStatusSampler(
            ObjectMapper objectMapper,
            PanoramaCenterClient centerClient,
            @Value("${statistics.device-status.storage-dir:data/statistics-device-status}") String storageDir,
            @Value("${statistics.device-status.retention-days:7}") int retentionDays) {
        this.objectMapper = objectMapper;
        this.centerClient = centerClient;
        this.storageDir = Paths.get(storageDir);
        this.retentionDays = Math.max(1, retentionDays);
        load();
    }

    @Scheduled(fixedDelayString = "${statistics.device-status.sample-interval-ms:300000}")
    public void sample() {
        try {
            List<Map<String, Object>> records = centerClient.deviceRegistry();
            String day = LocalDate.now(CHINA_ZONE).format(DAY_FORMATTER);
            long sampledAt = System.currentTimeMillis();
            for (Map<String, Object> record : records) {
                String serial = firstString(record, "robotId", "serialNumber");
                if (serial == null || serial.isBlank()) {
                    continue;
                }
                String type = firstString(record, "type");
                String status = classify(record);
                DaySample sample = samples.computeIfAbsent(serial, ignored -> new ConcurrentHashMap<>())
                        .computeIfAbsent(day, ignored -> new DaySample());
                sample.record(sampledAt, status, type);
            }
            persist(day);
        } catch (Exception ex) {
            log.warn("设备注册表采样失败", ex);
        }
    }

    /**
     * 返回设备在统计周期内各状态累计采样次数与采样跨度（秒）。
     * 返回 {@code long[4]} = {online, offline, fault, spanSeconds}。
     */
    public long[] countsInRange(String serial, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        long on = 0;
        long off = 0;
        long fault = 0;
        long firstTs = Long.MAX_VALUE;
        long lastTs = Long.MIN_VALUE;
        ConcurrentHashMap<String, DaySample> byDay = samples.get(serial);
        if (byDay == null) {
            return new long[]{0, 0, 0, 0};
        }
        for (LocalDate date = rangeStart.toLocalDate(); !date.isAfter(rangeEnd.toLocalDate()); date = date.plusDays(1)) {
            DaySample sample = byDay.get(date.format(DAY_FORMATTER));
            if (sample != null) {
                on += sample.online;
                off += sample.offline;
                fault += sample.fault;
                if (sample.firstTs > 0) {
                    firstTs = Math.min(firstTs, sample.firstTs);
                }
                if (sample.lastTs > 0) {
                    lastTs = Math.max(lastTs, sample.lastTs);
                }
            }
        }
        long span = (firstTs != Long.MAX_VALUE && lastTs > firstTs) ? (lastTs - firstTs) / 1000 : 0;
        return new long[]{on, off, fault, span};
    }

    /**
     * 按设备注册表快照判定状态：offline -> 离线；online 且健康状态异常 -> 故障；否则 -> 运行。
     */
    private String classify(Map<String, Object> record) {
        String status = firstString(record, "status");
        if (status != null && "offline".equalsIgnoreCase(status)) {
            return "offline";
        }
        String health = firstString(record, "healthStatus");
        if (isFaultHealth(health)) {
            return "fault";
        }
        return "online";
    }

    private boolean isFaultHealth(String health) {
        if (health == null || health.isBlank()) {
            return false;
        }
        String normalized = health.toUpperCase(Locale.ROOT);
        return "NORMAL".equals(normalized) ? false
                : (normalized.contains("ERROR")
                        || normalized.contains("FAULT")
                        || normalized.contains("异常")
                        || normalized.contains("故障"));
    }

    private void persist(String day) {
        try {
            Files.createDirectories(storageDir);
            Map<String, Map<String, Object>> dump = new LinkedHashMap<>();
            samples.forEach((serial, byDay) -> {
                DaySample sample = byDay.get(day);
                if (sample != null) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("firstTs", sample.firstTs);
                    values.put("lastTs", sample.lastTs);
                    values.put("type", sample.type);
                    values.put("online", sample.online);
                    values.put("offline", sample.offline);
                    values.put("fault", sample.fault);
                    dump.put(serial, values);
                }
            });
            Path target = storageDir.resolve(day + ".json");
            Path temp = storageDir.resolve(day + ".json.tmp");
            objectMapper.writeValue(temp.toFile(), dump);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.warn("设备状态采样持久化失败 day={}", day, ex);
        }
    }

    private void load() {
        try {
            if (!Files.isDirectory(storageDir)) {
                return;
            }
            LocalDate cutoff = LocalDate.now(CHINA_ZONE).minusDays(retentionDays);
            try (Stream<Path> paths = Files.list(storageDir)) {
                List<Path> files = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                        .toList();
                for (Path file : files) {
                    String day = file.getFileName().toString().replace(".json", "");
                    if (day.length() != 8) {
                        continue;
                    }
                    LocalDate date;
                    try {
                        date = LocalDate.parse(day, DAY_FORMATTER);
                    } catch (Exception ex) {
                        continue;
                    }
                    if (date.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        continue;
                    }
                    Map<String, Map<String, Object>> dump = objectMapper.readValue(
                            file.toFile(), new TypeReference<Map<String, Map<String, Object>>>() {
                            });
                    dump.forEach((serial, values) -> {
                        DaySample sample = new DaySample();
                        sample.firstTs = ((Number) values.getOrDefault("firstTs", 0L)).longValue();
                        sample.lastTs = ((Number) values.getOrDefault("lastTs", 0L)).longValue();
                        sample.type = stringValue(values.get("type"));
                        sample.online = ((Number) values.getOrDefault("online", 0)).intValue();
                        sample.offline = ((Number) values.getOrDefault("offline", 0)).intValue();
                        sample.fault = ((Number) values.getOrDefault("fault", 0)).intValue();
                        // 跳过缺少有效时间戳的旧格式数据，避免采样跨度退化为“自 1970 起”导致时长虚高。
                        if (sample.firstTs <= 0 || sample.lastTs <= 0) {
                            return;
                        }
                        samples.computeIfAbsent(serial, ignored -> new ConcurrentHashMap<>()).put(day, sample);
                    });
                }
            }
        } catch (IOException ex) {
            log.warn("设备状态采样数据加载失败", ex);
        }
    }

    private String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class DaySample {
        private volatile long firstTs = Long.MAX_VALUE;
        private volatile long lastTs = Long.MIN_VALUE;
        private volatile String type;
        private volatile int online;
        private volatile int offline;
        private volatile int fault;

        private synchronized void record(long sampledAt, String status, String type) {
            if (sampledAt < firstTs) {
                firstTs = sampledAt;
            }
            if (sampledAt > lastTs) {
                lastTs = sampledAt;
            }
            if (this.type == null || this.type.isBlank()) {
                this.type = type;
            }
            if ("online".equals(status)) {
                online++;
            } else if ("fault".equals(status)) {
                fault++;
            } else if ("offline".equals(status)) {
                offline++;
            }
        }
    }
}
