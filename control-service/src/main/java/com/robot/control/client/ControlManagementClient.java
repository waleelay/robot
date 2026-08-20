package com.robot.control.client;

import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.config.ControlProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
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
    private static final Duration DEFAULT_DEVICE_CACHE_TTL = Duration.ofSeconds(120);

    private final RestClient restClient;
    private final ControlProperties properties;
    private final RequestAuthorizationHeaders requestAuthorizationHeaders;
    private final Map<DeviceCacheKey, CachedDevice> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, CachedDevices> devicesCache = new ConcurrentHashMap<>();
    private final Map<String, CachedDictionary> dictionaryCache = new ConcurrentHashMap<>();

    /**
     * 创建 ControlManagementClient 实例。
     *
     * @param builder RestClient 构建器
     * @param properties 服务配置
     * @param requestAuthorizationHeaders 当前请求认证头透传器
     */
    public ControlManagementClient(
            RestClient.Builder builder,
            ControlProperties properties,
            RequestAuthorizationHeaders requestAuthorizationHeaders) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
        this.requestAuthorizationHeaders = requestAuthorizationHeaders;
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
        String cacheIdentity = cacheIdentity();
        DeviceCacheKey cacheKey = new DeviceCacheKey(cacheIdentity, serialNumber);
        CachedDevice cached = deviceCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.of(new LinkedHashMap<>(cached.device()));
        }
        Optional<Map<String, Object>> loaded = loadDeviceBySerialNumber(serialNumber, false)
                .or(() -> loadDeviceBySerialNumber(serialNumber, true));
        if (loaded.isPresent()) {
            Map<String, Object> snapshot = new LinkedHashMap<>(loaded.get());
            deviceCache.put(cacheKey, new CachedDevice(snapshot, Instant.now().plus(deviceTtl())));
            return Optional.of(new LinkedHashMap<>(snapshot));
        }
        deviceCache.remove(cacheKey);
        return Optional.empty();
    }

    /**
     * 查询已缓存的设备档案，不发起管理端请求。
     *
     * @param serialNumber 机器人序列号
     * @return 已缓存设备档案
     */
    public Optional<Map<String, Object>> cachedDeviceBySerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return Optional.empty();
        }
        Optional<String> currentCacheKey = requestAuthorizationHeaders.currentCacheKey();
        if (currentCacheKey.isPresent()) {
            String cacheIdentity = currentCacheKey.get();
            CachedDevice cached = deviceCache.get(new DeviceCacheKey(cacheIdentity, serialNumber));
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                return Optional.of(new LinkedHashMap<>(cached.device()));
            }
            return cachedListDevice(cacheIdentity, serialNumber);
        }
        CachedDevice cached = newestCachedDevice(serialNumber);
        if (cached != null) {
            return Optional.of(new LinkedHashMap<>(cached.device()));
        }
        Optional<Map<String, Object>> cachedListDevice = newestCachedListDevice(serialNumber);
        if (cachedListDevice.isPresent()) {
            return cachedListDevice;
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
        return devices(false);
    }

    private List<Map<String, Object>> devices(boolean forceRefresh) {
        String cacheIdentity = cacheIdentity();
        Instant now = Instant.now();
        CachedDevices cached = devicesCache.get(cacheIdentity);
        if (!forceRefresh && cached != null && cached.expiresAt().isAfter(now)) {
            return copyMaps(cached.devices());
        }
        List<Map<String, Object>> loaded = requestDevices();
        Instant expiresAt = now.plus(deviceTtl());
        List<Map<String, Object>> snapshot = copyMaps(loaded);
        devicesCache.put(cacheIdentity, new CachedDevices(snapshot, expiresAt));
        snapshot.forEach(device -> {
            String serialNumber = firstString(device, "serialNumber", "robotId");
            if (serialNumber != null && !serialNumber.isBlank()) {
                deviceCache.put(new DeviceCacheKey(cacheIdentity, serialNumber), new CachedDevice(new LinkedHashMap<>(device), expiresAt));
            }
        });
        return copyMaps(loaded);
    }

    private Optional<Map<String, Object>> loadDeviceBySerialNumber(String serialNumber, boolean forceRefresh) {
        return devices(forceRefresh).stream()
                .filter(device -> serialNumber.equals(string(device.get("serialNumber"))))
                .findFirst()
                .flatMap(device -> device(firstString(device, "id"))
                        .map(detail -> mergeDeviceDetail(device, detail))
                        .or(() -> Optional.of(device)));
    }

    private List<Map<String, Object>> requestDevices() {
        URI uri = uri("/api/v1/management/devices")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .build(true)
                .toUri();
        return records(uri);
    }

    private Duration deviceTtl() {
        int seconds = properties.getDeviceCacheTtlSeconds();
        return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_DEVICE_CACHE_TTL;
    }

    /** 定期清理已过期的用户设备缓存，避免 token 刷新或用户切换后旧条目长期占用内存。 */
    @Scheduled(fixedDelayString = "${control.device-cache-evict-delay-ms:60000}")
    void evictExpiredCaches() {
        Instant now = Instant.now();
        deviceCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        devicesCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        dictionaryCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    /**
     * 查询管理端固定摄像头列表。
     *
     * @return 固定摄像头列表
     */
    public List<Map<String, Object>> fixedCameras() {
        URI uri = uri("/api/v1/management/fixed-cameras")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .build(true)
                .toUri();
        return records(uri);
    }

    /**
     * 根据摄像头 ID 查询固定摄像头。
     *
     * @param cameraId 固定摄像头 ID
     * @return 固定摄像头
     */
    public Optional<Map<String, Object>> fixedCamera(String cameraId) {
        if (cameraId == null || cameraId.isBlank()) {
            return Optional.empty();
        }
        return fixedCameras().stream()
                .filter(camera -> cameraId.equals(firstString(camera, "cameraId", "id")))
                .findFirst();
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

    /**
     * 根据管理端设备类型字典解析设备类型名称。
     *
     * @param deviceType 管理端设备类型编码
     * @return 设备类型名称
     */
    public Optional<String> deviceTypeName(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return Optional.empty();
        }
        String code = deviceType.trim();
        return deviceTypeDictionary().stream()
                .filter(option -> code.equals(firstString(option, "value", "itemValue", "itemCode", "code")))
                .map(option -> firstString(option, "label", "itemName", "name"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private List<Map<String, Object>> deviceTypeDictionary() {
        String cacheIdentity = cacheIdentity();
        Instant now = Instant.now();
        CachedDictionary cached = dictionaryCache.get(cacheIdentity);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return copyMaps(cached.records());
        }
        URI uri = uri("/api/v1/management/selection-options/dictionaries/device_type")
                .build(true)
                .toUri();
        List<Map<String, Object>> loaded = records(uri);
        dictionaryCache.put(cacheIdentity, new CachedDictionary(copyMaps(loaded), now.plus(deviceTtl())));
        return copyMaps(loaded);
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
        return responseMap(uri)
                .map(response -> {
                    Object dataValue = response.get("data");
                    if (dataValue instanceof List<?> list) {
                        return maps(list);
                    }
                    Map<String, Object> data = map(dataValue);
                    if (data.isEmpty() && !response.containsKey("code") && !response.containsKey("data")) {
                        data = response;
                    }
                    if (data.isEmpty()) {
                        return List.<Map<String, Object>>of();
                    }
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
        return responseMap(uri)
                .flatMap(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> map) {
                        return Optional.of((Map<String, Object>) map);
                    }
                    if (!response.containsKey("code") && !response.containsKey("data")) {
                        return Optional.of(response);
                    }
                    return Optional.empty();
                });
    }

    private Optional<Map<String, Object>> responseMap(URI uri) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(requestAuthorizationHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Management service rejected request uri={} status={}",
                    uri,
                    exception.getStatusCode());
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("请求管理服务失败，请求地址={}", uri, exception);
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

    private String cacheIdentity() {
        return requestAuthorizationHeaders.currentCacheKey().orElse("background");
    }

    private CachedDevice newestCachedDevice(String serialNumber) {
        Instant now = Instant.now();
        return deviceCache.entrySet().stream()
                .filter(entry -> serialNumber.equals(entry.getKey().serialNumber()))
                .map(Map.Entry::getValue)
                .filter(device -> device.expiresAt().isAfter(now))
                .max((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
                .orElse(null);
    }

    private Optional<Map<String, Object>> cachedListDevice(String cacheIdentity, String serialNumber) {
        CachedDevices cached = devicesCache.get(cacheIdentity);
        if (cached == null || !cached.expiresAt().isAfter(Instant.now())) {
            return Optional.empty();
        }
        return cached.devices().stream()
                .filter(device -> serialNumber.equals(firstString(device, "serialNumber", "robotId")))
                .findFirst()
                .map(LinkedHashMap::new);
    }

    private Optional<Map<String, Object>> newestCachedListDevice(String serialNumber) {
        Instant now = Instant.now();
        return devicesCache.values().stream()
                .filter(devices -> devices.expiresAt().isAfter(now))
                .flatMap(devices -> devices.devices().stream())
                .filter(device -> serialNumber.equals(firstString(device, "serialNumber", "robotId")))
                .findFirst()
                .map(LinkedHashMap::new);
    }

    private List<Map<String, Object>> copyMaps(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().map(LinkedHashMap::new).map(map -> (Map<String, Object>) map).toList();
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

    private record DeviceCacheKey(String cacheIdentity, String serialNumber) {
    }

    private record CachedDevice(Map<String, Object> device, Instant expiresAt) {
    }

    private record CachedDevices(List<Map<String, Object>> devices, Instant expiresAt) {
    }

    private record CachedDictionary(List<Map<String, Object>> records, Instant expiresAt) {
    }
}
