package com.robot.control.mileage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Control 对 BFF 提供的里程统计接口。 */
@RestController
@RequestMapping("/api/control/statistics/mileage")
public class MileageController {

    private final MileageService mileageService;

    public MileageController(MileageService mileageService) {
        this.mileageService = mileageService;
    }

    @GetMapping
    public Map<String, Object> summary(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime,
            @RequestParam(required = false) List<String> robotIds) {
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("里程统计结束时间不能早于开始时间");
        }
        return mileageService.summary(startTime, endTime, robotIds);
    }
}
