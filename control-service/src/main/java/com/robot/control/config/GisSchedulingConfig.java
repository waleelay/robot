package com.robot.control.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** GIS 换算专用调度线程，避免外部调用阻塞其他状态扫描任务。 */
@Configuration(proxyBeanMethods = false)
public class GisSchedulingConfig {

    @Bean("taskScheduler")
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean("gisTaskScheduler")
    public ThreadPoolTaskScheduler gisTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("gis-location-").build();
    }
}
