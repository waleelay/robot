package com.robot.mediaserver.file.progress;

import com.robot.mediaserver.config.MediaProperties;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class FileProgressConfiguration {

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
