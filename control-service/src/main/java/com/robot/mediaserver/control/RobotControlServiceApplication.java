package com.robot.mediaserver.control;

import com.robot.mediaserver.control.config.ControlProperties;
import com.robot.mediaserver.control.config.ControlServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ControlProperties.class, ControlServiceProperties.class})
public class RobotControlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotControlServiceApplication.class, args);
    }
}
