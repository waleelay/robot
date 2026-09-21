package com.robot.mediaserver.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.video.FixedCameraIngressResponse;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.livekit.LiveKitIngressService;
import com.robot.mediaserver.livekit.LiveKitIngressService.CreateIngressRequest;
import com.robot.mediaserver.livekit.LiveKitIngressService.IngressInfo;
import com.robot.mediaserver.video.model.FixedCameraIngressOperation;
import com.robot.mediaserver.video.model.FixedCameraStreamStatus;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.mediaserver.video.repository.FixedCameraIngressRuntimeLockRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

/** 固定摄像头 RTMP Ingress 配置生命周期。 */
@Service
public class FixedCameraIngressService {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraIngressService.class);
    private static final long ORPHAN_PROTECTION_SECONDS = 60;

    private final MediaProperties properties;
    private final LiveKitIngressService liveKit;
    private final VideoSourceRuntimeRepository runtimeRepository;
    private final FixedCameraIngressRuntimeLockRepository lockRepository;
    private final ObjectMapper objectMapper;
    private final FixedCameraIngressPersistenceExceptionClassifier exceptionClassifier;
    private final VideoSessionService videoSessionService;
    private final PlatformTransactionManager transactionManager;
    private final Semaphore permits;

    public FixedCameraIngressService(
            MediaProperties properties,
            LiveKitIngressService liveKit,
            VideoSourceRuntimeRepository runtimeRepository,
            FixedCameraIngressRuntimeLockRepository lockRepository,
            ObjectMapper objectMapper,
            FixedCameraIngressPersistenceExceptionClassifier exceptionClassifier,
            VideoSessionService videoSessionService,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.liveKit = liveKit;
        this.runtimeRepository = runtimeRepository;
        this.lockRepository = lockRepository;
        this.objectMapper = objectMapper;
        this.exceptionClassifier = exceptionClassifier;
        this.videoSessionService = videoSessionService;
        this.transactionManager = transactionManager;
        this.permits = new Semaphore(properties.getLivekit().getIngressAdminMaxConcurrency(), true);
    }

    public FixedCameraIngressResponse create(String cameraId, long revision) {
        return mutate(cameraId, revision, FixedCameraIngressOperation.CREATE);
    }

    public FixedCameraIngressResponse rotate(String cameraId, long revision) {
        return mutate(cameraId, revision, FixedCameraIngressOperation.ROTATE);
    }

    public void revoke(String cameraId, long revision) {
        FixedCameraIngressResponse response = mutate(cameraId, revision, FixedCameraIngressOperation.REVOKE);
        if (response.publisherMode() == VideoPublisherMode.LIVEKIT_INGRESS) {
            videoSessionService.quiesceLiveKitIngress(cameraId);
        }
    }

    public FixedCameraIngressResponse get(String cameraId) {
        VideoSourceRuntime runtime = runtimeRepository
                .findBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQuality(
                        VideoSourceType.FIXED_CAMERA, cameraId, cameraId, VideoChannel.visible, VideoQuality.main)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "FIXED_CAMERA_INGRESS_NOT_CONFIGURED",
                        "固定摄像头尚未配置 RTMP 推流", false));
        return response(runtime, false, null, null);
    }

    public List<FixedCameraIngressResponse> statuses(List<String> cameraIds) {
        if (cameraIds == null || cameraIds.isEmpty()) {
            return List.of();
        }
        if (cameraIds.size() > 500) {
            throw new IllegalArgumentException("单次最多查询 500 个固定摄像头");
        }
        return cameraIds.stream().filter(id -> id != null && !id.isBlank()).distinct()
                .map(id -> {
                    try {
                        return get(id);
                    } catch (FixedCameraIngressException exception) {
                        return new FixedCameraIngressResponse(id, null, null, 0, 0, false, null, null,
                                FixedCameraStreamStatus.UNKNOWN.name(), "INGRESS_NOT_CONFIGURED", null,
                                false, null, null);
                    }
                }).toList();
    }

    /**
     * 复用视频维护任务巡检平台创建的 Ingress，收敛超时后的迟到资源和丢失映射。
     */
    public void reconcileManagedResources() {
        if (!permits.tryAcquire()) {
            log.debug("Ingress 管理操作进行中，本周期跳过资源对账");
            return;
        }
        try {
            Duration callTimeout = Duration.ofMillis(properties.getLivekit().getIngressLivekitCallTimeoutMs());
            List<IngressInfo> resources = liveKit.list(callTimeout);
            List<VideoSourceRuntime> runtimes = runtimeRepository.findByPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS);
            for (VideoSourceRuntime runtime : runtimes) {
                try {
                    reconcileRuntimeResources(runtime.getRuntimeId(), resources);
                } catch (RuntimeException exception) {
                    log.warn("固定摄像头 Ingress 资源对账失败 cameraId={} runtimeId={}",
                            runtime.getSourceId(), runtime.getRuntimeId(), exception);
                }
            }
            deleteUnownedOrphans(
                    resources,
                    runtimeRepository.findByPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS),
                    callTimeout);
        } catch (RuntimeException exception) {
            log.warn("获取 LiveKit Ingress 资源列表失败，本周期保留现有映射", exception);
        } finally {
            permits.release();
        }
    }

    private void reconcileRuntimeResources(String runtimeId, List<IngressInfo> resources) {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(properties.getLivekit().getIngressAdminOperationTimeoutMs());
        String revokedCameraId = inTransaction(deadline, status -> {
            VideoSourceRuntime runtime = lockRepository.lock(runtimeId, remaining(deadline)).orElse(null);
            if (runtime == null || runtime.getPublisherMode() != VideoPublisherMode.LIVEKIT_INGRESS) {
                return null;
            }
            List<IngressInfo> cameraResources = resources.stream()
                    .filter(resource -> belongsToCamera(resource, runtime.getSourceId()))
                    .toList();
            if (runtime.getAcceptedIngressOperation() == FixedCameraIngressOperation.REVOKE) {
                for (IngressInfo resource : cameraResources) {
                    liveKit.delete(resource.ingressId(), remaining(deadline));
                }
                runtime.setIngressId(null);
                clearObserved(runtime);
                runtime.setUpdatedAt(now());
                runtimeRepository.save(runtime);
                return runtime.getSourceId();
            }

            IngressInfo exact = cameraResources.stream()
                    .filter(resource -> metadataMatches(
                            resource,
                            runtime.getIngressOperationRevision(),
                            runtime.getAcceptedIngressOperation()))
                    .findFirst()
                    .orElse(null);
            IngressInfo mapped = runtime.getIngressId() == null ? null : cameraResources.stream()
                    .filter(resource -> runtime.getIngressId().equals(resource.ingressId()))
                    .findFirst()
                    .orElse(null);
            IngressInfo authoritative = exact != null ? exact : mapped;
            for (IngressInfo resource : cameraResources) {
                if (authoritative == null || !resource.ingressId().equals(authoritative.ingressId())) {
                    liveKit.delete(resource.ingressId(), remaining(deadline));
                }
            }
            if (authoritative == null) {
                runtime.setIngressId(null);
                clearObserved(runtime);
            } else {
                runtime.setIngressId(authoritative.ingressId());
                updateStatus(runtime, authoritative);
            }
            runtime.setUpdatedAt(now());
            runtimeRepository.save(runtime);
            return null;
        });
        if (revokedCameraId != null) {
            videoSessionService.quiesceLiveKitIngress(revokedCameraId);
        }
    }

    private void deleteUnownedOrphans(
            List<IngressInfo> resources,
            List<VideoSourceRuntime> runtimes,
            Duration timeout) {
        Map<String, VideoSourceRuntime> runtimeByCamera = runtimes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        VideoSourceRuntime::getSourceId,
                        runtime -> runtime,
                        (left, right) -> left));
        long orphanBefore = now().minusSeconds(ORPHAN_PROTECTION_SECONDS).toEpochSecond();
        resources.stream()
                .filter(this::isManagedResource)
                .filter(resource -> {
                    String cameraId = metadataValue(resource, "cameraId");
                    return !runtimeByCamera.containsKey(cameraId);
                })
                .filter(resource -> createdAtEpochSeconds(resource) < orphanBefore)
                .forEach(resource -> {
                    try {
                        liveKit.delete(resource.ingressId(), timeout);
                    } catch (RuntimeException exception) {
                        log.warn("删除孤儿 Ingress 失败 ingressId={}", resource.ingressId(), exception);
                    }
                });
    }

    private boolean belongsToCamera(IngressInfo resource, String cameraId) {
        return ("fixed-camera-" + cameraId).equals(resource.name())
                || ("fixed-camera:" + cameraId).equals(resource.participantIdentity())
                || cameraId.equals(metadataValue(resource, "cameraId"));
    }

    private boolean isManagedResource(IngressInfo resource) {
        return "robot-mediaserver".equals(metadataValue(resource, "managedBy"));
    }

    private long createdAtEpochSeconds(IngressInfo resource) {
        try {
            return Long.parseLong(metadataValue(resource, "createdAtEpochSeconds"));
        } catch (RuntimeException exception) {
            return Long.MAX_VALUE;
        }
    }

    private FixedCameraIngressResponse mutate(
            String cameraId, long revision, FixedCameraIngressOperation operation) {
        validate(cameraId, revision, operation);
        boolean acquired;
        try {
            acquired = permits.tryAcquire(properties.getLivekit().getIngressAdminPermitWaitMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw busy("等待 Ingress 管理许可被中断");
        }
        if (!acquired) {
            throw busy("Ingress 管理繁忙，请稍后重试");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(properties.getLivekit().getIngressAdminOperationTimeoutMs());
        AtomicReference<String> createdIngressId = new AtomicReference<>();
        try {
            String runtimeId = accept(cameraId, revision, operation, deadline);
            FixedCameraIngressResponse response = execute(
                    runtimeId, cameraId, revision, operation, deadline, createdIngressId);
            createdIngressId.set(null);
            return response;
        } catch (FixedCameraIngressException exception) {
            compensate(createdIngressId.get(), deadline, exception);
            throw exception;
        } catch (RuntimeException exception) {
            compensate(createdIngressId.get(), deadline, exception);
            if (hasCause(exception, ResourceAccessException.class)) {
                throw unavailable("LiveKit Ingress API 暂不可用");
            }
            var classification = exceptionClassifier.classify(exception);
            if (classification == FixedCameraIngressPersistenceExceptionClassifier.Classification.BUSY) {
                throw busy("Ingress Runtime 数据库锁竞争，请稍后重试");
            }
            if (classification == FixedCameraIngressPersistenceExceptionClassifier.Classification.UNAVAILABLE) {
                throw unavailable("Ingress Runtime 数据库暂不可用");
            }
            throw exception;
        } finally {
            permits.release();
        }
    }

    private String accept(String cameraId, long revision, FixedCameraIngressOperation operation, long deadline) {
        return inTransaction(deadline, status -> {
            Duration remaining = remaining(deadline);
            String runtimeId = runtimeId(cameraId);
            runtimeRepository.insertIfAbsent(
                    runtimeId, VideoSourceType.FIXED_CAMERA.name(), cameraId, cameraId,
                    VideoChannel.visible.name(), VideoQuality.main.name(), roomName(cameraId),
                    VideoPublisherMode.LIVEKIT_INGRESS.name(), 0, now());
            VideoSourceRuntime runtime = lockRepository.lock(runtimeId, remaining)
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "FIXED_CAMERA_INGRESS_NOT_CONFIGURED",
                            "固定摄像头 Ingress Runtime 不存在", false));
            requireIngressModeOrSafeRevoke(runtime, operation);
            if (revision < runtime.getIngressOperationRevision()) {
                throw error(HttpStatus.CONFLICT, "FIXED_CAMERA_MEDIA_OPERATION_STALE", "Ingress 操作版本已过期", false);
            }
            if (revision == runtime.getIngressOperationRevision()
                    && runtime.getAcceptedIngressOperation() != null
                    && runtime.getAcceptedIngressOperation() != operation) {
                throw error(HttpStatus.CONFLICT, "FIXED_CAMERA_MEDIA_OPERATION_CONFLICT", "Ingress 操作版本发生冲突", false);
            }
            if (revision > runtime.getIngressOperationRevision() || runtime.getAcceptedIngressOperation() == null) {
                runtime.setIngressOperationRevision(revision);
                runtime.setAcceptedIngressOperation(operation);
                runtime.setUpdatedAt(now());
                runtimeRepository.save(runtime);
            }
            return runtimeId;
        });
    }

    private FixedCameraIngressResponse execute(
            String runtimeId,
            String cameraId,
            long revision,
            FixedCameraIngressOperation operation,
            long deadline,
            AtomicReference<String> createdIngressId) {
        return inTransaction(deadline, status -> {
            VideoSourceRuntime runtime = lockRepository.lock(runtimeId, remaining(deadline))
                    .orElseThrow(() -> unavailable("Ingress Runtime 已消失"));
            requireAccepted(runtime, revision, operation);
            if (safeGatewayRevoke(runtime, operation)) {
                return response(runtime, false, null, null);
            }
            List<IngressInfo> resources = cameraResources(cameraId, deadline);

            if (operation == FixedCameraIngressOperation.CREATE && runtime.getIngressId() != null) {
                IngressInfo mapped = resources.stream()
                        .filter(item -> runtime.getIngressId().equals(item.ingressId())).findFirst().orElse(null);
                if (mapped != null) {
                    updateStatus(runtime, mapped);
                    return response(runtime, false, null, null);
                }
                runtime.setIngressId(null);
            }

            IngressInfo exact = resources.stream().filter(item -> metadataMatches(item, revision, operation)).findFirst().orElse(null);
            if (exact != null) {
                runtime.setIngressId(exact.ingressId());
                updateStatus(runtime, exact);
                runtimeRepository.save(runtime);
                return response(runtime, false, null, null);
            }

            for (IngressInfo resource : resources) {
                liveKit.delete(resource.ingressId(), remaining(deadline));
            }
            runtime.setIngressId(null);
            clearObserved(runtime);
            if (operation == FixedCameraIngressOperation.REVOKE) {
                runtime.setUpdatedAt(now());
                runtimeRepository.save(runtime);
                return response(runtime, false, null, null);
            }

            IngressInfo created = liveKit.create(new CreateIngressRequest(
                    "fixed-camera-" + cameraId,
                    roomName(cameraId),
                    "fixed-camera:" + cameraId,
                    metadata(cameraId, revision, operation)), remaining(deadline));
            createdIngressId.set(created.ingressId());
            requireAccepted(runtime, revision, operation);
            runtime.setIngressId(created.ingressId());
            updateStatus(runtime, created);
            runtimeRepository.save(runtime);
            return response(runtime, true, created.url(), created.streamKey());
        });
    }

    private void compensate(String ingressId, long deadline, Throwable original) {
        if (ingressId == null || ingressId.isBlank()) {
            return;
        }
        try {
            liveKit.delete(ingressId, remaining(deadline));
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }

    private List<IngressInfo> cameraResources(String cameraId, long deadline) {
        return liveKit.list(remaining(deadline)).stream()
                .filter(item -> belongsToCamera(item, cameraId))
                .toList();
    }

    private boolean metadataMatches(IngressInfo info, long revision, FixedCameraIngressOperation operation) {
        return operation != null
                && String.valueOf(revision).equals(metadataValue(info, "operationRevision"))
                && operation.name().equals(metadataValue(info, "operationType"));
    }

    private String metadataValue(IngressInfo info, String key) {
        if (info.participantMetadata() == null || info.participantMetadata().isBlank()) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(info.participantMetadata(), Map.class).get(key);
            return value == null ? null : String.valueOf(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String metadata(String cameraId, long revision, FixedCameraIngressOperation operation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("managedBy", "robot-mediaserver");
        metadata.put("sourceType", "FIXED_CAMERA");
        metadata.put("cameraId", cameraId);
        metadata.put("operationRevision", revision);
        metadata.put("operationType", operation.name());
        metadata.put("createdAtEpochSeconds", now().toEpochSecond());
        metadata.put("schemaVersion", 1);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw unavailable("生成 Ingress metadata 失败");
        }
    }

    private void updateStatus(VideoSourceRuntime runtime, IngressInfo info) {
        String status = info.status();
        runtime.setLastStreamStatus(FixedCameraStreamStatus.OFFLINE);
        runtime.setLastReasonCode("ENDPOINT_ERROR".equals(status) ? "RTMP_INGRESS_ERROR"
                : "ENDPOINT_BUFFERING".equals(status) ? "RTMP_BUFFERING" : "RTMP_TRACK_MISSING");
        runtime.setLastVerifiedAt(now());
        runtime.setUpdatedAt(now());
    }

    private void clearObserved(VideoSourceRuntime runtime) {
        runtime.setPublisherIdentity(null);
        runtime.setPublisherParticipantSid(null);
        runtime.setTrackSid(null);
        runtime.setTrackName(null);
        runtime.setLastMediaAt(null);
        runtime.setLastStreamStatus(FixedCameraStreamStatus.UNKNOWN);
        runtime.setLastReasonCode("INGRESS_NOT_CONFIGURED");
        runtime.setLastVerifiedAt(now());
    }

    private FixedCameraIngressResponse response(
            VideoSourceRuntime runtime, boolean issued, String url, String streamKey) {
        return new FixedCameraIngressResponse(
                runtime.getSourceId(), runtime.getIngressId(), runtime.getPublisherMode(),
                runtime.getPublisherRevision(), runtime.getIngressOperationRevision(),
                runtime.getIngressId() != null, runtime.getRoomName(), "fixed-camera:" + runtime.getSourceId(),
                runtime.getLastStreamStatus() == null ? FixedCameraStreamStatus.UNKNOWN.name()
                        : runtime.getLastStreamStatus().name(),
                runtime.getLastReasonCode(), runtime.getLastVerifiedAt(), issued,
                issued ? url : null, issued ? streamKey : null);
    }

    private void validate(String cameraId, long revision, FixedCameraIngressOperation operation) {
        if ((operation == FixedCameraIngressOperation.CREATE || operation == FixedCameraIngressOperation.ROTATE)
                && !properties.getLivekit().isIngressEnabled()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "INGRESS_DISABLED", "LiveKit Ingress 未启用", false);
        }
        if (cameraId == null || cameraId.isBlank() || revision < 0) {
            throw new IllegalArgumentException("cameraId 和操作版本必须有效");
        }
    }

    private void requireIngressMode(VideoSourceRuntime runtime) {
        if (runtime.getPublisherMode() != VideoPublisherMode.LIVEKIT_INGRESS) {
            throw error(HttpStatus.CONFLICT, "FIXED_CAMERA_PUBLISHER_MODE_TRANSITION",
                    "固定摄像头当前不是 LiveKit Ingress 发布模式", true);
        }
    }

    private void requireIngressModeOrSafeRevoke(
            VideoSourceRuntime runtime,
            FixedCameraIngressOperation operation) {
        if (!safeGatewayRevoke(runtime, operation)) {
            requireIngressMode(runtime);
        }
    }

    private boolean safeGatewayRevoke(
            VideoSourceRuntime runtime,
            FixedCameraIngressOperation operation) {
        return operation == FixedCameraIngressOperation.REVOKE
                && runtime.getPublisherMode() == VideoPublisherMode.FIXED_CAMERA_GATEWAY
                && runtime.getIngressId() == null;
    }

    private void requireAccepted(VideoSourceRuntime runtime, long revision, FixedCameraIngressOperation operation) {
        requireIngressModeOrSafeRevoke(runtime, operation);
        if (runtime.getIngressOperationRevision() != revision || runtime.getAcceptedIngressOperation() != operation) {
            throw error(HttpStatus.CONFLICT, "FIXED_CAMERA_MEDIA_OPERATION_STALE", "Ingress 操作已被更新版本取代", false);
        }
    }

    private Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw busy("Ingress 操作已超过总预算");
        }
        return Duration.ofNanos(nanos);
    }

    private <T> T inTransaction(long deadline, TransactionCallback<T> callback) {
        Duration budget = remaining(deadline);
        int timeoutSeconds = Math.max(1, Math.toIntExact(budget.toMillis() / 1000L));
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setTimeout(timeoutSeconds);
        return template.execute(callback);
    }

    private String runtimeId(String cameraId) {
        String key = VideoSourceType.FIXED_CAMERA + ":" + cameraId + ":" + cameraId + ":visible:main";
        return "runtime_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private String roomName(String cameraId) {
        return "media.fixed." + cameraId + ".visible.main";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private FixedCameraIngressException busy(String message) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "FIXED_CAMERA_INGRESS_BUSY", message, true);
    }

    private FixedCameraIngressException unavailable(String message) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "FIXED_CAMERA_INGRESS_UNAVAILABLE", message, true);
    }

    private FixedCameraIngressException error(HttpStatus status, String code, String message, boolean retryable) {
        return new FixedCameraIngressException(status, code, message, retryable);
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int index = 0; current != null && index < 64; index++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
