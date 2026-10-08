package com.robot.mediaserver.file.progress;

import com.robot.mediaserver.config.MediaProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;

/** 为文件进度回源配置有界执行器，并为分片合并租约配置独立续期调度器。 */
@Configuration
public class FileProgressConfiguration {

    /**
     * 创建有界进度回源执行器；工作队列已满时由调用线程执行，以限制积压。
     * @param properties 服务配置
     * @return 由容器管理的进度重建执行器
     */
    @Bean("fileProgressRebuildExecutor")
    public Executor fileProgressRebuildExecutor(MediaProperties properties) {
        int concurrency = Math.max(1, properties.getFile().getProgressRebuildConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("file-progress-");
        // 队列已满时由查询线程执行回源，保持有界内存且避免整批请求被拒绝。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * 创建分片合并租约续期调度器。
     *
     * @return 由容器管理并在关闭时释放的调度器
     */
    @Bean("fileCompletionLeaseScheduler")
    public TaskScheduler fileCompletionLeaseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("file-completion-lease-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
