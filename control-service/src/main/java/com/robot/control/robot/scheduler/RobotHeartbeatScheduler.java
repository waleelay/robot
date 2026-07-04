package com.robot.control.robot.scheduler;

import com.robot.control.robot.service.RobotRegistryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RobotHeartbeatScheduler {

    private final RobotRegistryService registryService;

    public RobotHeartbeatScheduler(RobotRegistryService registryService) {
        this.registryService = registryService;
    }

    @Scheduled(fixedDelayString = "${control.robot.heartbeat-sweep-delay-ms:5000}")
    public void sweep() {
        registryService.sweepOffline();
    }
}
