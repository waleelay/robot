package com.robot.mediaserver.robot.api;

import com.robot.mediaserver.robot.dto.RobotDeviceResponse;
import com.robot.mediaserver.robot.dto.RobotCameraResponse;
import com.robot.mediaserver.robot.service.RobotRegistryService;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/media/robots", "/internal/media/robots"})
public class RobotDeviceController {

    private final RobotRegistryService registryService;
    private final ObjectMapper objectMapper;

    public RobotDeviceController(RobotRegistryService registryService, ObjectMapper objectMapper) {
        this.registryService = registryService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<RobotDeviceResponse> list() {
        return registryService.list();
    }

    @PostMapping("/client-status")
    public boolean clientStatus(@RequestBody Map<String, Object> data) {
        String robotId = String.valueOf(data.get("robotId"));
        String clientId = String.valueOf(data.get("clientId"));
        String status = String.valueOf(data.get("status"));
        String name = data.get("name") == null ? robotId : String.valueOf(data.get("name"));
        String type = data.get("type") == null ? "机器人" : String.valueOf(data.get("type"));
        Integer battery = data.get("battery") instanceof Number value ? value.intValue() : null;
        List<RobotCameraResponse> cameras = objectMapper.convertValue(
                data.getOrDefault("cameras", List.of()),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RobotCameraResponse.class));
        return registryService.update(robotId, clientId, status, name, type, battery, cameras);
    }
}
