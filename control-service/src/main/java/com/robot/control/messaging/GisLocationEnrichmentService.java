package com.robot.control.messaging;

import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlManagementClient.GisConversion;
import com.robot.control.client.ControlManagementClient.GisCoordinate;
import com.robot.control.robot.service.RobotRegistryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 将边缘端缺失经纬度的位置按设备去重后批量交给 Management 换算。 */
@Component
public class GisLocationEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(GisLocationEnrichmentService.class);
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_PENDING_DEVICES = 1000;
    private static final long RESULT_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final ControlManagementClient managementClient;
    private final RobotRegistryService robotRegistryService;
    private final Map<String, PendingLocation> pending = new LinkedHashMap<>();
    private final Map<String, CachedResult> results = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> failedMaps = new LinkedHashMap<>(16, 0.75f, true);

    public GisLocationEnrichmentService(
            ControlManagementClient managementClient,
            RobotRegistryService robotRegistryService) {
        this.managementClient = managementClient;
        this.robotRegistryService = robotRegistryService;
    }

    /** 观察最新定位；边缘端已有合法经纬度时仅使旧换算请求失效。 */
    public void observe(String serialNumber, Map<String, Object> location) {
        PendingLocation item;
        CachedResult cached;
        synchronized (pending) {
            if (hasValidGis(location)) {
                pending.remove(serialNumber);
                results.remove(serialNumber);
                return;
            }
            String mapId = text(location.get("mapId"));
            Double x = finiteNumber(location.get("x"));
            Double y = finiteNumber(location.get("y"));
            if (mapId == null || x == null || y == null) {
                pending.remove(serialNumber);
                return;
            }
            item = new PendingLocation(serialNumber, mapId, x, y);
            cached = results.get(serialNumber);
            if (cached != null && cached.expired()) {
                results.remove(serialNumber);
                cached = null;
            }
            if (cached != null && cached.location().equals(item)) {
                pending.remove(serialNumber);
            } else {
                cached = null;
                Long failedUntil = failedMaps.get(mapId);
                if (failedUntil != null && System.nanoTime() - failedUntil < 0) {
                    pending.remove(serialNumber);
                    return;
                }
                failedMaps.remove(mapId);
                if (pending.size() >= MAX_PENDING_DEVICES && !pending.containsKey(serialNumber)) {
                    log.debug("GIS 换算等待队列已满，已丢弃定位，设备标识={}", serialNumber);
                    return;
                }
                pending.put(serialNumber, item);
                return;
            }
        }
        if (cached.converted()) {
            enrich(item, cached.longitude(), cached.latitude());
        }
    }

    @Scheduled(fixedDelay = 1000, scheduler = "gisTaskScheduler")
    void flush() {
        List<PendingLocation> batch = drainBatch().stream()
                .filter(item -> robotRegistryService.isConnected(item.serialNumber()))
                .toList();
        if (batch.isEmpty()) {
            return;
        }
        List<GisCoordinate> request = batch.stream()
                .map(item -> new GisCoordinate(item.serialNumber(), item.mapId(), item.x(), item.y()))
                .toList();
        List<GisConversion> conversions;
        try {
            conversions = managementClient.convertGis(request);
        } catch (RuntimeException exception) {
            log.warn("批量 GIS 坐标换算失败，设备数={} 原因={}", batch.size(), exception.toString());
            return;
        }
        Map<String, GisConversion> conversionsBySerialNumber = new LinkedHashMap<>();
        if (conversions != null) {
            conversions.forEach(result -> conversionsBySerialNumber.put(result.serialNumber(), result));
        }
        for (PendingLocation item : batch) {
            GisConversion result = conversionsBySerialNumber.get(item.serialNumber());
            if (result == null || !matches(item, result)) {
                continue;
            }
            boolean converted = result.converted() && validGis(result.longitude(), result.latitude());
            cache(item, converted ? result.longitude() : null, converted ? result.latitude() : null, converted);
            if (converted) {
                clearMapFailure(item.mapId());
                enrich(item, result.longitude(), result.latitude());
            } else {
                cacheMapFailure(item.mapId(), result.reason());
                log.debug("GIS 坐标换算失败，设备标识={} 原因={}", item.serialNumber(), result.reason());
            }
        }
    }

    private void clearMapFailure(String mapId) {
        synchronized (pending) {
            failedMaps.remove(mapId);
        }
    }

    private void cacheMapFailure(String mapId, String reason) {
        if (!"MAP_NOT_FOUND".equals(reason) && !"NO_VALID_CONTROL_POINTS".equals(reason)) {
            return;
        }
        synchronized (pending) {
            failedMaps.put(mapId, System.nanoTime() + RESULT_TTL_NANOS);
            pending.values().removeIf(item -> mapId.equals(item.mapId()));
            while (failedMaps.size() > MAX_PENDING_DEVICES) {
                failedMaps.remove(failedMaps.keySet().iterator().next());
            }
        }
    }

    private void cache(PendingLocation item, Double longitude, Double latitude, boolean converted) {
        synchronized (pending) {
            results.put(item.serialNumber(), new CachedResult(
                    item, longitude, latitude, converted, System.nanoTime() + RESULT_TTL_NANOS));
            if (item.equals(pending.get(item.serialNumber()))) {
                pending.remove(item.serialNumber());
            }
            while (results.size() > MAX_PENDING_DEVICES) {
                results.remove(results.keySet().iterator().next());
            }
        }
    }

    private void enrich(PendingLocation item, Double longitude, Double latitude) {
        try {
            robotRegistryService.enrichLocationIfConnected(
                    item.serialNumber(), item.mapId(), item.x(), item.y(), longitude, latitude);
        } catch (RuntimeException exception) {
            log.warn("GIS 坐标写入失败，设备标识={} 原因={}",
                    item.serialNumber(), exception.toString());
        }
    }

    private List<PendingLocation> drainBatch() {
        List<PendingLocation> batch = new ArrayList<>(MAX_BATCH_SIZE);
        synchronized (pending) {
            var iterator = pending.values().iterator();
            while (iterator.hasNext() && batch.size() < MAX_BATCH_SIZE) {
                batch.add(iterator.next());
                iterator.remove();
            }
        }
        return batch;
    }

    private boolean matches(PendingLocation request, GisConversion result) {
        return request.mapId().equals(result.mapId())
                && sameNumber(request.x(), result.x())
                && sameNumber(request.y(), result.y());
    }

    private boolean hasValidGis(Map<String, Object> location) {
        return validGis(
                finiteNumber(location.get("longitude")),
                finiteNumber(location.get("latitude")));
    }

    private boolean validGis(Double longitude, Double latitude) {
        return longitude != null && longitude >= -180 && longitude <= 180
                && latitude != null && latitude >= -90 && latitude <= 90;
    }

    private boolean sameNumber(Double first, Double second) {
        return first != null && second != null && Double.compare(first, second) == 0;
    }

    private Double finiteNumber(Object value) {
        try {
            double number = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private record PendingLocation(String serialNumber, String mapId, Double x, Double y) {
    }

    private record CachedResult(
            PendingLocation location,
            Double longitude,
            Double latitude,
            boolean converted,
            long expiresAtNanos) {

        private boolean expired() {
            return System.nanoTime() - expiresAtNanos >= 0;
        }
    }
}
