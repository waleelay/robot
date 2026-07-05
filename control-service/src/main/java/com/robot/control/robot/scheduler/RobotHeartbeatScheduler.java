package com.robot.control.robot.scheduler;

import com.robot.control.robot.service.RobotRegistryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 机器人心跳离线扫描任务。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class RobotHeartbeatScheduler {

    private final RobotRegistryService registryService;

    /**
     * 创建 RobotHeartbeatScheduler 实例。
     *
     * @param registryService 机器人注册表服务
     */
    public RobotHeartbeatScheduler(RobotRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * 执行周期扫描任务。
     */
    @Scheduled(fixedDelayString = "${control.robot.heartbeat-sweep-delay-ms:5000}")
    public void sweep() {
        registryService.sweepOffline();
    }
}
