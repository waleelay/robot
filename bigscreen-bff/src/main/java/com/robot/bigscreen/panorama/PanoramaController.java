package com.robot.bigscreen.panorama;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bigscreen/panorama")
public class PanoramaController {

    private final PanoramaService panoramaService;

    public PanoramaController(PanoramaService panoramaService) {
        this.panoramaService = panoramaService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return panoramaService.overview();
    }

    @GetMapping("/devices/{deviceId}")
    public Map<String, Object> deviceDetail(@PathVariable String deviceId) {
        return panoramaService.deviceDetail(deviceId);
    }

    @GetMapping("/maps/{mapId}/resources")
    public Map<String, Object> mapResources(@PathVariable String mapId) {
        return panoramaService.mapResources(mapId);
    }

    @GetMapping("/maps/{mapId}/task-routes")
    public Map<String, Object> mapTaskRoutes(@PathVariable String mapId) {
        return panoramaService.mapTaskRoutes(mapId);
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> taskDetail(@PathVariable String taskId) {
        return panoramaService.taskDetail(taskId);
    }

    @GetMapping("/tasks/{taskId}/fixed-cameras")
    public Map<String, Object> taskFixedCameras(@PathVariable String taskId) {
        return panoramaService.taskFixedCameras(taskId);
    }

    @GetMapping("/tasks")
    public Map<String, Object> tasks() {
        return panoramaService.tasks();
    }

    @GetMapping("/alarms")
    public Map<String, Object> alarms() {
        return panoramaService.alarms();
    }

    @GetMapping("/alarms/actionable-workflow")
    public Map<String, Object> actionableWorkflowAlarms() {
        return panoramaService.actionableWorkflowAlarms();
    }

    @PostMapping("/alarms/{alarmId}/disposal")
    public Map<String, Object> disposeAlarm(
            @PathVariable String alarmId,
            @RequestBody Map<String, Object> request) {
        return panoramaService.disposeAlarm(alarmId, request);
    }

    @PostMapping("/alarms/{alarmId}/handle-and-continue")
    public Map<String, Object> handleWorkflowAlarm(
            @PathVariable String alarmId,
            @RequestBody Map<String, Object> request) {
        return panoramaService.handleWorkflowAlarm(alarmId, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "code", "BAD_REQUEST",
                "message", exception.getMessage()));
    }
}
