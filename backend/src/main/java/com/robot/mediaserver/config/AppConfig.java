package com.robot.mediaserver.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用基础配置。
 *
 * @author leelay
 * @date 2026/05/19
 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class AppConfig {
}
