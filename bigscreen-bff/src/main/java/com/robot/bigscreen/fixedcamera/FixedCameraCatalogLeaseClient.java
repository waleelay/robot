package com.robot.bigscreen.fixedcamera;

import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 将当前用户已获授权的固定摄像头配置以短租约同步给 Control。
 *
 * <p>租约只在 Management 完整分页查询成功后生成。同步失败不放大用户权限，
 * Gateway 中的旧租约会按时过期并退化为 UNKNOWN。</p>
 */
@Component
public class FixedCameraCatalogLeaseClient {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraCatalogLeaseClient.class);

    private final CenterServiceProperties properties;
    private final RestClient restClient;
    private final AtomicLong versionSequence = new AtomicLong();
    private final ConcurrentMap<String, LeaseRefreshState> refreshStates = new ConcurrentHashMap<>();

    @Value("${bigscreen.fixed-camera-catalog-lease.enabled:true}")
    private boolean enabled = true;

    @Value("${bigscreen.fixed-camera-catalog-lease.duration-seconds:180}")
    private long durationSeconds = 180L;

    public FixedCameraCatalogLeaseClient(CenterServiceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    /** 同步当前身份可见的摄像头目录。 */
    public void synchronize(
            Principal principal,
            HttpHeaders authorizationHeaders,
            List<Map<String, Object>> cameras) {
        if (!enabled) {
            return;
        }
        try {
            Instant now = Instant.now();
            LeaseRequest request = leaseRequest(principal, authorizationHeaders, cameras, now);
            if (!shouldSynchronize(request, now)) {
                return;
            }
            restClient.put()
                    .uri(controlUri())
                    .header("X-Internal-Caller", "bigscreen-bff")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            refreshStates.put(request.leaseId(), new LeaseRefreshState(
                    request.cameras(), now.plusSeconds(Math.max(15L, boundedDurationSeconds() / 2L))));
            log.debug("固定摄像头目录租约已同步，租约={} 摄像头数={}", request.leaseId(), request.cameras().size());
        } catch (RuntimeException exception) {
            log.warn("同步固定摄像头目录租约失败，将由旧租约过期收口", exception);
        }
    }

    /**
     * 主动释放当前身份的目录租约。失败时不影响 WebSocket 关闭，旧租约仍会按时过期。
     */
    public void release(Principal principal, HttpHeaders authorizationHeaders) {
        if (!enabled) {
            return;
        }
        String leaseId = leaseId(principal, authorizationHeaders);
        refreshStates.remove(leaseId);
        try {
            restClient.delete()
                    .uri(releaseUri(leaseId))
                    .header("X-Internal-Caller", "bigscreen-bff")
                    .retrieve()
                    .toBodilessEntity();
            log.info("固定摄像头目录租约已主动释放");
        } catch (RuntimeException exception) {
            log.warn("主动释放固定摄像头目录租约失败，将由过期机制收口", exception);
        }
    }

    LeaseRequest leaseRequest(
            Principal principal,
            HttpHeaders authorizationHeaders,
            List<Map<String, Object>> cameras,
            Instant now) {
        long boundedDuration = boundedDurationSeconds();
        return new LeaseRequest(
                leaseId(principal, authorizationHeaders),
                nextVersion(now),
                now,
                now.plusSeconds(boundedDuration),
                normalize(cameras));
    }

    private boolean shouldSynchronize(LeaseRequest request, Instant now) {
        LeaseRefreshState previous = refreshStates.get(request.leaseId());
        return previous == null
                || !previous.cameras().equals(request.cameras())
                || !now.isBefore(previous.renewAfter());
    }

    private long boundedDurationSeconds() {
        return Math.max(30L, Math.min(durationSeconds, 300L));
    }

    private long nextVersion(Instant now) {
        long base = now.toEpochMilli() * 1000L;
        return versionSequence.updateAndGet(previous -> Math.max(previous + 1L, base));
    }

    private List<CameraRecord> normalize(List<Map<String, Object>> sources) {
        Map<String, CameraRecord> result = new LinkedHashMap<>();
        if (sources == null) {
            return List.of();
        }
        for (Map<String, Object> source : sources) {
            if (source == null) {
                continue;
            }
            Optional<String> cameraId = firstString(source, "cameraId", "id");
            if (cameraId.isEmpty()) {
                continue;
            }
            result.put(cameraId.get(), new CameraRecord(
                    cameraId.get(),
                    booleanValue(source.get("enabled")),
                    firstString(source, "protocolType", "protocol").orElse("RTSP"),
                    firstString(source, "mainStreamUrl", "mainRtspUrl").orElse(null),
                    firstString(source, "subStreamUrl", "subRtspUrl").orElse(null)));
        }
        return List.copyOf(new ArrayList<>(result.values()));
    }

    private String leaseId(Principal principal, HttpHeaders headers) {
        String identity;
        if (principal instanceof JwtAuthenticationToken jwtAuthentication) {
            String issuer = jwtAuthentication.getToken().getIssuer() == null
                    ? "" : jwtAuthentication.getToken().getIssuer().toString();
            String orgId = String.valueOf(jwtAuthentication.getToken().getClaims()
                    .getOrDefault("org_id", jwtAuthentication.getToken().getClaims().getOrDefault("orgId", "")));
            identity = issuer + "|" + jwtAuthentication.getToken().getSubject() + "|" + orgId;
        } else {
            identity = String.valueOf(headers.getFirst(HttpHeaders.AUTHORIZATION));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private URI controlUri() {
        String baseUrl = properties.getControlBaseUrl();
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/internal/control/fixed-camera-catalog-leases")
                .build(true)
                .toUri();
    }

    private URI releaseUri(String leaseId) {
        String baseUrl = properties.getControlBaseUrl();
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/internal/control/fixed-camera-catalog-leases/{leaseId}")
                .buildAndExpand(leaseId)
                .encode()
                .toUri();
    }

    private Optional<String> firstString(Map<String, Object> source, String... names) {
        for (String name : names) {
            Object value = source.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return Optional.of(String.valueOf(value).trim());
            }
        }
        return Optional.empty();
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    public record LeaseRequest(
            String leaseId,
            long version,
            Instant issuedAt,
            Instant expiresAt,
            List<CameraRecord> cameras) {
    }

    public record CameraRecord(
            String cameraId,
            boolean enabled,
            String protocolType,
            String mainStreamUrl,
            String subStreamUrl) {
    }

    private record LeaseRefreshState(List<CameraRecord> cameras, Instant renewAfter) {
    }
}
