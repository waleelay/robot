package com.robot.control;

import com.robot.control.config.ControlProperties;
import com.robot.control.config.ControlServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Control Service 独立进程启动入口。
 *
 * @author leelay
 * @date 2026-07-05
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ControlProperties.class, ControlServiceProperties.class})
public class RobotControlServiceApplication {

    /**
     * 启动 Spring Boot 控制服务应用。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RobotControlServiceApplication.class, args);
    }
}
