package com.robot.mediaserver.robot.api;

import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.robot.service.RobotRegistryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/media/robots", "/internal/media/robots"})
public class RobotDeviceController {

    private final RobotRegistryService registryService;

    public RobotDeviceController(RobotRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping
    public List<RobotDeviceResponse> list() {
        return registryService.list();
    }
}
