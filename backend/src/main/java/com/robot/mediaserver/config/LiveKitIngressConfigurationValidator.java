package com.robot.mediaserver.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * LiveKit Ingress 管理能力的启动配置校验。
 */
@Component
public class LiveKitIngressConfigurationValidator {

    private final MediaProperties properties;
    private final DataSource dataSource;

    public LiveKitIngressConfigurationValidator(MediaProperties properties, DataSource dataSource) {
        this.properties = properties;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void validate() {
        MediaProperties.Livekit livekit = properties.getLivekit();
        if (!livekit.isIngressEnabled()) {
            return;
        }
        HikariDataSource hikari;
        try {
            hikari = dataSource.unwrap(HikariDataSource.class);
        } catch (Exception exception) {
            throw new IllegalStateException("启用 LiveKit Ingress 时数据源必须支持 Hikari 容量校验", exception);
        }
        validate(livekit, hikari.getMaximumPoolSize());
    }

    static void validate(MediaProperties.Livekit livekit, int maximumPoolSize) {
        int concurrency = livekit.getIngressAdminMaxConcurrency();
        if (concurrency <= 0) {
            throw new IllegalStateException("LIVEKIT_INGRESS_MAX_CONCURRENCY 必须大于 0");
        }
        if (livekit.getIngressAdminPermitWaitMs() <= 0
                || livekit.getIngressLivekitCallTimeoutMs() <= 0
                || livekit.getIngressAdminOperationTimeoutMs() <= 0
                || livekit.getIngressStatusStaleSeconds() <= 0) {
            throw new IllegalStateException("LiveKit Ingress 超时配置必须全部大于 0");
        }
        if (livekit.getIngressLivekitCallTimeoutMs() > livekit.getIngressAdminOperationTimeoutMs()) {
            throw new IllegalStateException("LiveKit Ingress 单次调用超时不能超过操作总预算");
        }
        int requiredPoolSize = Math.max(4, concurrency * 4);
        if (maximumPoolSize < requiredPoolSize) {
            throw new IllegalStateException(
                    "启用 LiveKit Ingress 时 Hikari maximumPoolSize 至少为 " + requiredPoolSize
                            + "，当前为 " + maximumPoolSize);
        }
    }
}
