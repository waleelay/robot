package com.robot.control.fixedcamera;

import com.robot.control.config.ControlServiceProperties;
import com.robot.control.messaging.RobotMediaCommandService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 管理 BFF 下发的用户授权目录租约，并只向 Gateway 下发当前有效的合并快照。
 */
@Service
public class FixedCameraCatalogLeaseService {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraCatalogLeaseService.class);

    private final ControlServiceProperties properties;
    private final RobotMediaCommandService commandService;
    private final Map<String, LeaseState> leases = new ConcurrentHashMap<>();
    private final AtomicLong catalogVersion = new AtomicLong();

    @Value("${control.fixed-camera-catalog.max-lease-seconds:300}")
    private long maxLeaseSeconds = 300L;

    public FixedCameraCatalogLeaseService(
            ControlServiceProperties properties,
            RobotMediaCommandService commandService) {
        this.properties = properties;
        this.commandService = commandService;
    }

    /** 接收并发布一份新租约；乱序版本不覆盖新数据。 */
    public synchronized FixedCameraCatalogSnapshot upsert(FixedCameraCatalogLeaseRequest request) {
        Instant now = Instant.now();
        validate(request, now);
        LeaseState current = leases.get(request.leaseId());
        if (current != null && request.version() <= current.version()) {
            return snapshot(now);
        }
        leases.put(request.leaseId(), new LeaseState(
                request.version(), request.expiresAt(), normalize(request.cameras())));
        return publish(now);
    }

    /**
     * 最后一个大屏会话关闭时主动撤销其目录租约，避免仍按旧租约继续探测。
     *
     * @return 是否实际移除了租约
     */
    public synchronized boolean release(String leaseId) {
        if (leaseId == null || leaseId.isBlank() || leases.remove(leaseId) == null) {
            return false;
        }
        FixedCameraCatalogSnapshot snapshot = publish(Instant.now());
        log.info("固定摄像头目录租约已主动释放，剩余摄像头数={}", snapshot.cameras().size());
        return true;
    }

    /** 移除过期租约并发布收口快照。 */
    @Scheduled(fixedDelayString = "${control.fixed-camera-catalog.sweep-delay-ms:1000}")
    public synchronized void expireLeases() {
        expireLeases(Instant.now());
    }

    synchronized void expireLeases(Instant now) {
        boolean changed = leases.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (changed) {
            publish(now);
        }
    }

    FixedCameraCatalogSnapshot currentSnapshot(Instant now) {
        return snapshot(now);
    }

    private FixedCameraCatalogSnapshot publish(Instant now) {
        FixedCameraCatalogSnapshot snapshot = snapshot(now);
        commandService.sendFixedCameraCatalog(snapshot);
        return snapshot;
    }

    private FixedCameraCatalogSnapshot snapshot(Instant now) {
        Map<String, SelectedCamera> selected = new LinkedHashMap<>();
        leases.values().stream()
                .filter(lease -> lease.expiresAt().isAfter(now))
                .sorted(Comparator.comparingLong(LeaseState::version))
                .forEach(lease -> lease.cameras().forEach((cameraId, camera) ->
                        selected.put(cameraId, new SelectedCamera(camera, lease.expiresAt()))));
        List<FixedCameraCatalogSnapshot.CameraRecord> cameras = selected.values().stream()
                .map(item -> new FixedCameraCatalogSnapshot.CameraRecord(
                        item.camera().cameraId(),
                        item.camera().enabled(),
                        item.camera().protocolType(),
                        item.camera().mainStreamUrl(),
                        item.camera().subStreamUrl(),
                        item.expiresAt()))
                .sorted(Comparator.comparing(FixedCameraCatalogSnapshot.CameraRecord::cameraId))
                .toList();
        return new FixedCameraCatalogSnapshot(
                "1.0",
                gatewayId(),
                catalogVersion.incrementAndGet(),
                now,
                cameras);
    }

    private Map<String, FixedCameraCatalogLeaseRequest.CameraRecord> normalize(
            List<FixedCameraCatalogLeaseRequest.CameraRecord> cameras) {
        Map<String, FixedCameraCatalogLeaseRequest.CameraRecord> result = new LinkedHashMap<>();
        if (cameras == null) {
            return Map.of();
        }
        if (cameras.size() > 5000) {
            throw new IllegalArgumentException("固定摄像头目录租约超过最大记录数");
        }
        for (FixedCameraCatalogLeaseRequest.CameraRecord camera : cameras) {
            if (camera == null || blank(camera.cameraId())) {
                continue;
            }
            result.put(camera.cameraId().trim(), new FixedCameraCatalogLeaseRequest.CameraRecord(
                    camera.cameraId().trim(),
                    camera.enabled(),
                    blank(camera.protocolType()) ? "RTSP" : camera.protocolType().trim(),
                    trimToNull(camera.mainStreamUrl()),
                    trimToNull(camera.subStreamUrl())));
        }
        return Map.copyOf(result);
    }

    private void validate(FixedCameraCatalogLeaseRequest request, Instant now) {
        if (request == null || blank(request.leaseId()) || request.version() <= 0L
                || request.issuedAt() == null || request.expiresAt() == null) {
            throw new IllegalArgumentException("固定摄像头目录租约缺少必填字段");
        }
        long boundedMax = Math.max(30L, Math.min(maxLeaseSeconds, 300L));
        if (!request.expiresAt().isAfter(now)
                || request.expiresAt().isAfter(request.issuedAt().plusSeconds(boundedMax))) {
            throw new IllegalArgumentException("固定摄像头目录租约过期时间超出边界");
        }
        if (request.issuedAt().isAfter(now.plusSeconds(30))
                || request.issuedAt().isBefore(now.minusSeconds(30))) {
            throw new IllegalArgumentException("固定摄像头目录租约签发时间超出允许时钟偏差");
        }
    }

    private String gatewayId() {
        String configured = properties.getMqtt().getFixedCameraGatewayId();
        return blank(configured) ? "default" : configured.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private record LeaseState(
            long version,
            Instant expiresAt,
            Map<String, FixedCameraCatalogLeaseRequest.CameraRecord> cameras) {
    }

    private record SelectedCamera(FixedCameraCatalogLeaseRequest.CameraRecord camera, Instant expiresAt) {
    }
}
