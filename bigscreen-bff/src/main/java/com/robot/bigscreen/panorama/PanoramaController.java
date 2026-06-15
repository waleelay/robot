package com.robot.bigscreen.panorama;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bigscreen/panorama")
public class PanoramaController {

    private final PanoramaMockService panoramaMockService;

    public PanoramaController(PanoramaMockService panoramaMockService) {
        this.panoramaMockService = panoramaMockService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return panoramaMockService.overview();
    }

    @GetMapping("/devices/{deviceId}")
    public Map<String, Object> deviceDetail(@PathVariable String deviceId) {
        return panoramaMockService.deviceDetail(deviceId);
    }

    @GetMapping("/device-groups")
    public Map<String, Object> deviceGroups() {
        return panoramaMockService.deviceGroups();
    }

    @GetMapping("/tasks")
    public Map<String, Object> tasks() {
        return panoramaMockService.tasks();
    }

    @GetMapping("/alarms")
    public Map<String, Object> alarms() {
        return panoramaMockService.alarms();
    }
}
