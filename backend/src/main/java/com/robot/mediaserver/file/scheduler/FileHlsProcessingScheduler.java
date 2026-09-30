package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileHlsProcessingService;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 按配置并发数领取视频处理任务，跟踪正在执行的文件并在销毁时停止执行器。 */
@Component
public class FileHlsProcessingScheduler {

    private final MediaProperties properties;
    private final FileHlsProcessingService service;
    /**
     * 实例内 HLS 后处理执行器；线程数和等待队列均受配置并发数限制，销毁时中断关闭。
     */
    private final ThreadPoolExecutor executor;
    /**
     * 本实例已提交且尚未结束的文件 ID 集合；任务 finally 移除，不提供跨实例互斥。
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 初始化 FileHlsProcessingScheduler，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param service 领取视频处理任务，管理 FFmpeg/ffprobe 子进程及临时文件，并收口 HLS 成功或失败状态。
     */
    public FileHlsProcessingScheduler(MediaProperties properties, FileHlsProcessingService service) {
        this.properties = properties;
        this.service = service;
        int workers = Math.max(1, properties.getFile().getHlsWorkerConcurrency());
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers), runnable ->
                        new Thread(runnable, "media-hls-" + sequence.incrementAndGet()), new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 在可用并发槽位内领取 HLS 任务；与停机串行，关闭后不再领取。
     */
    @Scheduled(fixedDelayString = "${media.file.hls-poll-delay-ms:3000}")
    public synchronized void poll() {
        if (!properties.getFile().isEnabled() || executor.isShutdown()) {
            return;
        }
        while (inFlight.size() < executor.getMaximumPoolSize()) {
            var candidate = service.claimNext();
            if (candidate.isEmpty() || !inFlight.add(candidate.get())) {
                return;
            }
            String fileId = candidate.get();
            executor.submit(() -> {
                try {
                    service.process(fileId);
                } finally {
                    inFlight.remove(fileId);
                }
            });
        }
    }

    /**
     * 停止领取任务并关闭后处理执行器，避免停机期间继续扩张工作队列。
     */
    @PreDestroy
    public synchronized void shutdown() {
        executor.shutdownNow();
    }
}
