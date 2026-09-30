package com.robot.bigscreen.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供大屏统计查询及统计报告创建、下载入口。 */
@RestController
@RequestMapping("/api/bigscreen/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 初始化 StatisticsController，保存所需依赖及初始运行状态。
     *
     * @param statisticsService 聚合大屏统计指标并生成和读取用户所属的统计报告。
     */
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询指定时间范围和设备类别的统计指标；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param range 统计时间范围编码
     * @param startTime 上海时区区间起点，包含该时刻
     * @param endTime 上海时区区间终点，包含该时刻
     * @param deviceType 平台设备类型编码
     * @param areaId 区域筛选标识；统计接口目前仅保留该请求值
     * @return 查询指定时间范围和设备类别的统计指标的接口响应
     */
    @Operation(
            operationId = "statisticsController_overview",
            summary = "查询指定时间范围和设备类别的统计指标",
            description = "查询指定时间范围和设备类别的统计指标。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"StatisticsController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "all") String deviceType,
            @RequestParam(required = false) String areaId) {
        return statisticsService.overview(range, startTime, endTime, deviceType, areaId);
    }

    /**
     * 生成当前用户所属的 PDF 统计报告并下载；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param request 请求参数
     * @param authentication 经过认证的当前用户上下文
     * @return 生成当前用户所属的 PDF 统计报告并下载的接口响应
     */
    @Operation(
            operationId = "statisticsController_exportReport",
            summary = "生成当前用户所属的 PDF 统计报告并下载",
            description = "生成当前用户所属的 PDF 统计报告并下载。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"StatisticsController"})
    @ApiResponse(
            responseCode = "200",
            description = "响应正文；下载接口保留 Content-Type 与 Content-Disposition",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        StatisticsService.ReportFile report = statisticsService.createReport(request, authentication);
        return pdfResponse(report);
    }

    /**
     * 分页查询当前用户所属的报告历史；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param page 页码
     * @param size 分页大小
     * @param authentication 经过认证的当前用户上下文
     * @return 分页查询当前用户所属的报告历史的接口响应
     */
    @Operation(
            operationId = "statisticsController_reportHistoryList",
            summary = "分页查询当前用户所属的报告历史",
            description = "分页查询当前用户所属的报告历史。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"StatisticsController"})
    @ApiResponse(
            responseCode = "200",
            description = "处理成功，状态及可空字段以响应为准",
            useReturnTypeSchema = true,
            content = @Content(mediaType = "application/json"))
    @GetMapping("/reports")
    public Map<String, Object> reportHistoryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return statisticsService.reportHistoryList(page, size, authentication);
    }

    /**
     * 下载当前用户所属的 PDF 报告；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param id 当前业务记录的唯一标识
     * @param authentication 经过认证的当前用户上下文
     * @return 下载当前用户所属的 PDF 报告的接口响应
     */
    @Operation(
            operationId = "statisticsController_downloadReport",
            summary = "下载当前用户所属的 PDF 报告",
            description = "下载当前用户所属的 PDF 报告。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"StatisticsController"})
    @ApiResponse(
            responseCode = "200",
            description = "响应正文；下载接口保留 Content-Type 与 Content-Disposition",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/reports/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id, Authentication authentication) {
        StatisticsService.ReportFile report = statisticsService.reportFile(id, authentication);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return pdfResponse(report);
    }

    /**
     * 删除当前用户所属的统计报告；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param id 当前业务记录的唯一标识
     * @param authentication 经过认证的当前用户上下文
     * @return 删除当前用户所属的统计报告的接口响应
     */
    @Operation(
            operationId = "statisticsController_deleteReport",
            summary = "删除当前用户所属的统计报告",
            description = "删除当前用户所属的统计报告。浏览器身份由 BFF JWT 安全配置检查，聚合结果可带数据质量标识。",
            tags = {"StatisticsController"})
    @ApiResponse(responseCode = "204", description = "处理完成，无响应正文", content = @Content)
    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable String id, Authentication authentication) {
        if (!statisticsService.deleteReport(id, authentication)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> pdfResponse(StatisticsService.ReportFile report) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(report.bytes());
    }
}
