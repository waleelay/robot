package com.robot.bigscreen.panorama;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供全景概览、设备和任务等大屏聚合 HTTP 入口。 */
@RestController
@RequestMapping("/api/bigscreen/panorama")
public class PanoramaController {

    private final PanoramaService panoramaService;

    /**
     * 初始化 PanoramaController，保存所需依赖及初始运行状态。
     *
     * @param panoramaService 聚合下游设备、任务、地图和告警，维护请求缓存与数据质量标识。
     */
    public PanoramaController(PanoramaService panoramaService) {
        this.panoramaService = panoramaService;
    }

    /**
     * 聚合设备、任务、地图、告警和里程概览；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 聚合设备、任务、地图、告警和里程概览的接口响应
     */
    @Operation(
            operationId = "panoramaController_overview",
            summary = "聚合设备、任务、地图、告警和里程概览",
            description = "聚合设备、任务、地图、告警和里程概览。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return panoramaService.overview();
    }

    /**
     * 查询设备挂载组件数量；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param deviceId 设备 ID
     * @return 查询设备挂载组件数量的接口响应
     */
    @Operation(
            operationId = "panoramaController_mountedDeviceCount",
            summary = "查询设备挂载组件数量",
            description = "查询设备挂载组件数量。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/devices/{deviceId}/mounted-device-count")
    public Map<String, Object> mountedDeviceCount(@PathVariable String deviceId) {
        return panoramaService.mountedDeviceCount(deviceId);
    }

    /**
     * 查询地图关联的设备和资源；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param mapId 所属地图 ID
     * @return 查询地图关联的设备和资源的接口响应
     */
    @Operation(
            operationId = "panoramaController_mapResources",
            summary = "查询地图关联的设备和资源",
            description = "查询地图关联的设备和资源。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/maps/{mapId}/resources")
    public Map<String, Object> mapResources(@PathVariable String mapId) {
        return panoramaService.mapResources(mapId);
    }

    /**
     * 查询地图内任务计划路线；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param mapId 所属地图 ID
     * @return 查询地图内任务计划路线的接口响应
     */
    @Operation(
            operationId = "panoramaController_mapTaskRoutes",
            summary = "查询地图内任务计划路线",
            description = "查询地图内任务计划路线。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/maps/{mapId}/task-routes")
    public Map<String, Object> mapTaskRoutes(@PathVariable String mapId) {
        return panoramaService.mapTaskRoutes(mapId);
    }

    /**
     * 查询任务计划及实例详情；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param taskId 任务计划 ID
     * @return 查询任务计划及实例详情的接口响应
     */
    @Operation(
            operationId = "panoramaController_taskDetail",
            summary = "查询任务计划及实例详情",
            description = "查询任务计划及实例详情。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> taskDetail(@PathVariable String taskId) {
        return panoramaService.taskDetail(taskId);
    }

    /**
     * 查询任务关联的固定摄像头；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param taskId 任务计划 ID
     * @return 查询任务关联的固定摄像头的接口响应
     */
    @Operation(
            operationId = "panoramaController_taskFixedCameras",
            summary = "查询任务关联的固定摄像头",
            description = "查询任务关联的固定摄像头。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/tasks/{taskId}/fixed-cameras")
    public Map<String, Object> taskFixedCameras(@PathVariable String taskId) {
        return panoramaService.taskFixedCameras(taskId);
    }

    /**
     * 查询任务计划与执行实例聚合结果；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 查询任务计划与执行实例聚合结果的接口响应
     */
    @Operation(
            operationId = "panoramaController_tasks",
            summary = "查询任务计划与执行实例聚合结果",
            description = "查询任务计划与执行实例聚合结果。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/tasks")
    public Map<String, Object> tasks() {
        return panoramaService.tasks();
    }

    /**
     * 查询当前告警聚合结果；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 查询当前告警聚合结果的接口响应
     */
    @Operation(
            operationId = "panoramaController_alarms",
            summary = "查询当前告警聚合结果",
            description = "查询当前告警聚合结果。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/alarms")
    public Map<String, Object> alarms() {
        return panoramaService.alarms();
    }

    /**
     * 按等级和时间分页查询告警；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param level 当前模型定义的等级或级别
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页请求的记录数量
     * @param occurredFrom 告警发生时间下界
     * @param occurredTo 告警发生时间上界
     * @return 按等级和时间分页查询告警的接口响应
     */
    @Operation(
            operationId = "panoramaController_alarmPage",
            summary = "按等级和时间分页查询告警",
            description = "按等级和时间分页查询告警。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/alarms/page")
    public Map<String, Object> alarmPage(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String occurredFrom,
            @RequestParam(required = false) String occurredTo) {
        return panoramaService.alarmPage(level, pageNum, pageSize, occurredFrom, occurredTo);
    }

    /**
     * 查询当前可处置的工作流告警；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @return 查询当前可处置的工作流告警的接口响应
     */
    @Operation(
            operationId = "panoramaController_actionableWorkflowAlarms",
            summary = "查询当前可处置的工作流告警",
            description = "查询当前可处置的工作流告警。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/alarms/actionable-workflow")
    public Map<String, Object> actionableWorkflowAlarms() {
        return panoramaService.actionableWorkflowAlarms();
    }

    /**
     * 提交普通告警处置动作；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param alarmId 待查询或处置的告警 ID
     * @param request 请求参数
     * @return 提交普通告警处置动作的接口响应
     */
    @Operation(
            operationId = "panoramaController_handleAlarm",
            summary = "提交普通告警处置动作",
            description = "提交普通告警处置动作。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/alarms/{alarmId}/handled")
    public Map<String, Object> handleAlarm(
            @PathVariable String alarmId,
            @RequestBody Map<String, Object> request) {
        return panoramaService.handleAlarm(alarmId, request);
    }

    /**
     * 处置工作流告警并请求继续任务；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param alarmId 待查询或处置的告警 ID
     * @param request 请求参数
     * @return 处置工作流告警并请求继续任务的接口响应
     */
    @Operation(
            operationId = "panoramaController_handleWorkflowAlarm",
            summary = "处置工作流告警并请求继续任务",
            description = "处置工作流告警并请求继续任务。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"PanoramaController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @PostMapping("/alarms/{alarmId}/handle-and-continue")
    public Map<String, Object> handleWorkflowAlarm(
            @PathVariable String alarmId,
            @RequestBody Map<String, Object> request) {
        return panoramaService.handleWorkflowAlarm(alarmId, request);
    }

    /**
     * 将全景参数校验失败转换为当前 BAD_REQUEST 业务响应。
     *
     * @param exception 需要分类或映射的原始异常
     * @return 包含失败标志、业务码及说明的 HTTP 400 响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "code", "BAD_REQUEST",
                "message", exception.getMessage()));
    }
}
