package com.robot.bigscreen.statistics;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bigscreen/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "all") String deviceType,
            @RequestParam(required = false) String areaId) {
        return statisticsService.overview(range, startTime, endTime, deviceType, areaId);
    }

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestBody Map<String, Object> request) {
        StatisticsService.ReportFile report = statisticsService.createReport(request);
        return pdfResponse(report);
    }

    @GetMapping("/reports")
    public Map<String, Object> reportHistoryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return statisticsService.reportHistoryList(page, size);
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String id) {
        StatisticsService.ReportFile report = statisticsService.reportFile(id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return pdfResponse(report);
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable String id) {
        if (!statisticsService.deleteReport(id)) {
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
