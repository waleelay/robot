package com.robot.mediaserver.file.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileHlsProcessingService;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 验证 HLS 调度不超出配置并发，停机后不再领取任务。 */
class FileHlsProcessingSchedulerTest {
    @Test
    void boundsConcurrencyAndDoesNotClaimAfterShutdown() throws Exception {
        MediaProperties properties = new MediaProperties();
        properties.getFile().setEnabled(true);
        properties.getFile().setHlsWorkerConcurrency(1);
        FileHlsProcessingService service = mock(FileHlsProcessingService.class);
        AtomicInteger claims = new AtomicInteger();
        when(service.claimNext()).thenAnswer(invocation -> Optional.of("file-" + claims.incrementAndGet()));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            throw new IllegalStateException("处理失败");
        }).when(service).process("file-1");
        FileHlsProcessingScheduler scheduler = new FileHlsProcessingScheduler(properties, service);
        try {
            scheduler.poll();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.poll();
            assertThat(claims.get()).isEqualTo(1);
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
        clearInvocations(service);
        scheduler.poll();
        verifyNoInteractions(service);
    }
}
