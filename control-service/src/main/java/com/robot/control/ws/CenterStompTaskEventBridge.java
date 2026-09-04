package com.robot.control.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.ControlServiceProperties;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 将中心端 STOMP 失效通知桥接到本地普通 WebSocket。 */
@Component
public class CenterStompTaskEventBridge implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CenterStompTaskEventBridge.class);
    private static final int MAX_SEEN_EVENTS = 1000;

    private final ControlServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final MediaWebSocketPublisher publisher;
    private final TaskScheduler taskScheduler;
    private final RestClient restClient;
    private final WebSocketStompClient stompClient;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final Map<String, Boolean> seenEvents = new LinkedHashMap<>();
    private volatile StompSession session;

    public CenterStompTaskEventBridge(
            ControlServiceProperties properties,
            ObjectMapper objectMapper,
            MediaWebSocketPublisher publisher,
            TaskScheduler taskScheduler,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.taskScheduler = taskScheduler;
        this.restClient = restClientBuilder.build();
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(objectMapper);
        this.stompClient.setMessageConverter(messageConverter);
        this.stompClient.setTaskScheduler(taskScheduler);
        this.stompClient.setDefaultHeartbeat(new long[] {10_000, 10_000});
    }

    @Override
    public void start() {
        if (!properties.getCenterStomp().isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        connect();
    }

    @Override
    public void stop() {
        running.set(false);
        StompSession current = session;
        session = null;
        if (current != null && current.isConnected()) {
            current.disconnect();
        }
        stompClient.stop();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void connect() {
        if (!running.get() || !connecting.compareAndSet(false, true)) {
            return;
        }
        try {
            String token = accessToken();
            String websocketUrl = websocketUrl(token);
            WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
            StompHeaders connectHeaders = new StompHeaders();
            if (StringUtils.hasText(token)) {
                handshakeHeaders.setBearerAuth(token);
                connectHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    websocketUrl,
                    handshakeHeaders,
                    connectHeaders,
                    new StompSessionHandlerAdapter() {
                        @Override
                        public void afterConnected(StompSession connected, StompHeaders headers) {
                            onConnected(connected);
                            log.info("中心端 STOMP 事件桥接已连接，订阅主题={}", properties.getCenterStomp().getTopic());
                        }

                        @Override
                        public void handleTransportError(StompSession failed, Throwable exception) {
                            session = null;
                            connecting.set(false);
                            log.warn("中心端 STOMP 事件桥接已断开：{}", exception.getMessage());
                            scheduleReconnect();
                        }
                    });
            future.exceptionally(exception -> {
                connecting.set(false);
                log.warn("中心端 STOMP 事件桥接连接失败：{}", exception.getMessage());
                scheduleReconnect();
                return null;
            });
        } catch (RuntimeException exception) {
            connecting.set(false);
            log.warn("中心端 STOMP 事件桥接初始化失败：{}", exception.getMessage());
            scheduleReconnect();
        }
    }

    /** 初次连接和重连都补查任务，恢复断线期间遗漏的变化。 */
    void onConnected(StompSession connected) {
        session = connected;
        connecting.set(false);
        subscribe(connected);
        publisher.publish("management.task.invalidated", Map.of("scopes", List.of("PLAN", "EXECUTION")));
    }

    private void subscribe(StompSession connected) {
        connected.subscribe(properties.getCenterStomp().getTopic(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof JsonNode event) {
                    handleEvent(event);
                }
            }
        });
    }

    void handleEvent(byte[] payload) {
        try {
            handleEvent(objectMapper.readTree(payload));
        } catch (Exception exception) {
            log.warn("已忽略无效的中心端 STOMP 事件", exception);
        }
    }

    void handleEvent(JsonNode event) {
        String type = event.path("type").asText();
        JsonNode scopes = event.path("data").path("scopes");
        if (!"alarm.changed.v1".equals(type)
                && !("task.changed.v1".equals(type) && taskScope(scopes))) {
            return;
        }
        String source = event.path("source").asText();
        String eventId = event.path("id").asText();
        if (!register(source + ":" + eventId)) {
            return;
        }
        if ("alarm.changed.v1".equals(type)) {
            log.debug("转发中心端告警事件，来源={} eventId={}", source, eventId);
            publisher.publish("management.alarm.invalidated", Map.of(
                    "source", source,
                    "eventId", eventId));
            return;
        }
        List<String> eventScopes = scopes(scopes);
        log.debug("转发中心端任务事件，来源={} eventId={} 范围={}", source, eventId, eventScopes);
        publisher.publish("management.task.invalidated", Map.of(
                "source", source,
                "eventId", eventId,
                "scopes", eventScopes));
    }

    private boolean taskScope(JsonNode scopes) {
        if (!scopes.isArray()) {
            return false;
        }
        for (JsonNode scope : scopes) {
            if ("PLAN".equals(scope.asText()) || "EXECUTION".equals(scope.asText())) {
                return true;
            }
        }
        return false;
    }

    private List<String> scopes(JsonNode scopes) {
        List<String> result = new ArrayList<>();
        scopes.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private synchronized boolean register(String key) {
        if (seenEvents.containsKey(key)) {
            return false;
        }
        seenEvents.put(key, Boolean.TRUE);
        if (seenEvents.size() > MAX_SEEN_EVENTS) {
            seenEvents.remove(seenEvents.keySet().iterator().next());
        }
        return true;
    }

    private void scheduleReconnect() {
        if (running.get()) {
            taskScheduler.schedule(this::connect, Instant.now().plusMillis(properties.getCenterStomp().getReconnectDelayMs()));
        }
    }

    private String websocketUrl(String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getCenterStomp().getWebsocketUrl());
        if (StringUtils.hasText(token)) {
            builder.replaceQueryParam("access_token", token);
        }
        return builder.build().encode().toUriString();
    }

    private String accessToken() {
        ControlServiceProperties.CenterStomp config = properties.getCenterStomp();
        if (StringUtils.hasText(config.getAccessToken())) {
            return config.getAccessToken();
        }
        if (!StringUtils.hasText(config.getTokenUrl())
                || !StringUtils.hasText(config.getClientId())
                || !StringUtils.hasText(config.getClientSecret())) {
            return null;
        }
        String body = "grant_type=client_credentials&client_id=" + encode(config.getClientId())
                + "&client_secret=" + encode(config.getClientSecret());
        JsonNode response = restClient.post()
                .uri(config.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String token = response == null ? null : response.path("access_token").asText(null);
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("center STOMP token response has no access_token");
        }
        return token;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
