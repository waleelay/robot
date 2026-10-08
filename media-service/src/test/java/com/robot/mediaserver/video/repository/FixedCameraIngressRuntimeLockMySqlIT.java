package com.robot.mediaserver.video.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.mediaserver.video.service.FixedCameraIngressPersistenceExceptionClassifier;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用专用 MySQL 测试库验证行锁硬截止；默认测试不会连接外部数据库。 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FixedCameraIngressRuntimeLockRepository.class)
@EnabledIfEnvironmentVariable(named = "MEDIA_MYSQL_IT_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FixedCameraIngressRuntimeLockMySqlIT {

    private static final String RUNTIME_ID = "runtime_mysql_lock_it";

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MEDIA_MYSQL_IT_URL"));
        registry.add("spring.datasource.username", () -> env("MEDIA_MYSQL_IT_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> env("MEDIA_MYSQL_IT_PASSWORD", ""));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 2);
    }

    @Autowired
    private FixedCameraIngressRuntimeLockRepository lockRepository;

    @Autowired
    private VideoSourceRuntimeRepository runtimeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void insertRuntime() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            VideoSourceRuntime runtime = new VideoSourceRuntime();
            runtime.setRuntimeId(RUNTIME_ID);
            runtime.setSourceType(VideoSourceType.FIXED_CAMERA);
            runtime.setSourceId("camera-mysql-it");
            runtime.setDeviceId("camera-mysql-it");
            runtime.setChannel(VideoChannel.visible);
            runtime.setQuality(VideoQuality.main);
            runtime.setRoomName("media.fixed.camera-mysql-it.visible.main");
            runtime.setPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS);
            runtime.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            runtime.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            runtimeRepository.saveAndFlush(runtime);
        });
    }

    @Test
    void competingLockFailsWithinIngressDeadline() throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                lockRepository.lock(RUNTIME_ID, Duration.ofSeconds(5)).orElseThrow();
                lockHeld.countDown();
                try {
                    assertThat(releaseLock.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            try {
                Throwable failure = catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> lockRepository.lock(RUNTIME_ID, Duration.ofSeconds(1))));
                assertThat(failure).isNotNull();
                assertThat(new FixedCameraIngressPersistenceExceptionClassifier().classify(failure))
                        .isEqualTo(FixedCameraIngressPersistenceExceptionClassifier.Classification.BUSY);
                assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
            } finally {
                releaseLock.countDown();
            }
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
