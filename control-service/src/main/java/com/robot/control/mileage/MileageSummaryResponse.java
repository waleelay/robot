package com.robot.control.mileage;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 区间里程查询结果；无有效样本时保留 null，避免与真实零里程混淆。
 *
 * @author Codex
 * @date 2026-09-29
 * @param startTime 上海时区区间起点，包含该时刻
 * @param endTime 上海时区区间终点，包含该时刻
 * @param timezone 统计时区
 * @param hasData 是否存在有效样本
 * @param totalMeters 有效里程总量，单位米
 * @param sampleCount 有效样本总数
 * @param unit 里程单位
 * @param byRobot 按机器人拆分的结果
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "持久化分钟桶的里程汇总；不包含质量评级字段")
public record MileageSummaryResponse(
        @Schema(description = "区间起点，Asia/Shanghai", example = "2026-08-14 00:00:00",
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", requiredMode = Schema.RequiredMode.REQUIRED)
        String startTime,
        @Schema(description = "区间终点，Asia/Shanghai", example = "2026-08-14 23:59:59",
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", requiredMode = Schema.RequiredMode.REQUIRED)
        String endTime,
        @Schema(description = "统计时区", allowableValues = "Asia/Shanghai", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(description = "是否存在有效里程样本", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasData,
        @Schema(description = "总里程（米）；无样本为 null，有样本且未移动为 0", types = {"number", "null"},
                example = "1256.800", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalMeters,
        @Schema(description = "有效样本总数", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        long sampleCount,
        @Schema(description = "计量单位", allowableValues = "m", requiredMode = Schema.RequiredMode.REQUIRED)
        String unit,
        @Schema(description = "指定机器人时保留去重后的请求顺序；未指定时按机器人标识排序",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<RobotMileageSummary> byRobot) {

    /**
     * 单个机器人的区间里程。
     *
     * @param robotId 机器人标识
     * @param hasData 是否存在有效样本
     * @param mileageMeters 里程米数，无数据时为 null
     * @param sampleCount 有效样本数
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "单个机器人的有效里程")
    public record RobotMileageSummary(
            @Schema(description = "机器人标识", example = "robot-001", requiredMode = Schema.RequiredMode.REQUIRED)
            String robotId,
            @Schema(description = "是否存在有效样本", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean hasData,
            @Schema(description = "里程（米）；无有效样本为 null", types = {"number", "null"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal mileageMeters,
            @Schema(description = "有效样本数", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            long sampleCount) {
    }
}
