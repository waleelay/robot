package com.robot.control.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** GIS 换算专用调度线程，避免外部调用阻塞其他状态扫描任务。 */
@Configuration(proxyBeanMethods = false)
public class GisSchedulingConfig {

    /**
     * 创建常规业务调度器，使周期任务由容器管理生命周期。
     * @param builder Spring 线程池任务调度器构建器
     * @return 业务任务调度器
     */
    @Bean("taskScheduler")
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    /**
     * 创建 GIS 坐标转换专用调度器，隔离外部转换延迟。
     * @param builder Spring 线程池任务调度器构建器
     * @return GIS 任务调度器
     */
    @Bean("gisTaskScheduler")
    public ThreadPoolTaskScheduler gisTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("gis-location-").build();
    }
}
