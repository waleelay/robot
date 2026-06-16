package com.robot.bigscreen.statistics;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bigscreen/statistics")
public class StatisticsController {

    private final StatisticsMockService statisticsMockService;

    public StatisticsController(StatisticsMockService statisticsMockService) {
        this.statisticsMockService = statisticsMockService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "all") String deviceType,
            @RequestParam(required = false) String areaId) {
        return statisticsMockService.overview(range, startTime, endTime, deviceType, areaId);
    }

    @PostMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestBody Map<String, Object> request) {
        byte[] bytes = statisticsMockService.exportPdf(request);
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = "statistics-report-" + date + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(bytes);
    }
}
