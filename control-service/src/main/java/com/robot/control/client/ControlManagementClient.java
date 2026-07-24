package com.robot.control.client;

import com.robot.control.config.ControlProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Control Service 调用 Management Service 查询设备档案的 HTTP 客户端。
 *
 * @author leelay
 * @date 2026-07-24
 */
@Component
public class ControlManagementClient {

    private static final Logger log = LoggerFactory.getLogger(ControlManagementClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final Duration DEVICE_CACHE_TTL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final ControlProperties properties;
    private final Map<String, CachedDevice> deviceCache = new ConcurrentHashMap<>();

    /**
     * 创建 ControlManagementClient 实例。
     *
     * @param builder RestClient 构建器
     * @param properties 服务配置
     */
    public ControlManagementClient(RestClient.Builder builder, ControlProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
    }

    /**
     * 根据机器人序列号查询管理端设备详情。
     *
     * @param serialNumber 机器人序列号
     * @return 设备详情
     */
    public Optional<Map<String, Object>> deviceBySerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return Optional.empty();
        }
        CachedDevice cached = deviceCache.get(serialNumber);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(new LinkedHashMap<>(cached.device()));
        }
        Optional<Map<String, Object>> loaded = devices().stream()
                .filter(device -> serialNumber.equals(string(device.get("serialNumber"))))
                .findFirst()
                .flatMap(device -> device(firstString(device, "id"))
                        .map(detail -> mergeDeviceDetail(device, detail))
                        .or(() -> Optional.of(device)));
        if (loaded.isPresent()) {
            Map<String, Object> snapshot = new LinkedHashMap<>(loaded.get());
            deviceCache.put(serialNumber, new CachedDevice(snapshot, now.plus(DEVICE_CACHE_TTL)));
            return Optional.of(new LinkedHashMap<>(snapshot));
        }
        return cached == null
                ? Optional.empty()
                : Optional.of(new LinkedHashMap<>(cached.device()));
    }

    /**
     * 查询管理端设备列表。
     *
     * @return 设备列表
     */
    public List<Map<String, Object>> devices() {
        URI uri = uri("/api/v1/management/devices")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 100)
                .build(true)
                .toUri();
        return records(uri);
    }

    /**
     * 查询管理端设备详情。
     *
     * @param id 管理端设备 ID
     * @return 设备详情
     */
    public Optional<Map<String, Object>> device(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri("/api/v1/management/devices/" + id).build(true).toUri();
        return dataMap(uri);
    }

    private Map<String, Object> mergeDeviceDetail(Map<String, Object> listDevice, Map<String, Object> detail) {
        Map<String, Object> result = new LinkedHashMap<>(listDevice);
        Map<String, Object> device = map(detail.get("device"));
        device.forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        Object components = detail.get("components");
        if (components instanceof List<?> list) {
            result.put("components", maps(list));
        }
        return result;
    }

    private List<Map<String, Object>> records(URI uri) {
        return dataMap(uri)
                .map(data -> {
                    Object records = data.get("records");
                    if (records instanceof List<?> list) {
                        return maps(list);
                    }
                    return List.<Map<String, Object>>of(data);
                })
                .orElseGet(List::of);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> dataMap(URI uri) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                return Optional.empty();
            }
            Object data = response.get("data");
            if (data instanceof Map<?, ?> map) {
                return Optional.of((Map<String, Object>) map);
            }
            if (!response.containsKey("code") && !response.containsKey("data")) {
                return Optional.of(response);
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Failed to request management service uri={}", uri, exception);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) item))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(properties.getManagementServiceBaseUrl()).path(path);
    }

    private String firstString(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = string(source.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record CachedDevice(Map<String, Object> device, Instant expiresAt) {
    }
}
