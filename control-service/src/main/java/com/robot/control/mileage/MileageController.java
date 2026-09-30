package com.robot.control.mileage;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Control 对 BFF 提供的里程统计接口。 */
@RestController
@RequestMapping("/api/control/statistics/mileage")
@Tag(name = "里程统计", description = "供 BFF 调用的受控内网查询")
public class MileageController {

    private final MileageService mileageService;

    /**
     * 初始化 MileageController，保存所需依赖及初始运行状态。
     *
     * @param mileageService 计算设备里程增量，并按分钟保存可用于统计的结果。
     */
    public MileageController(MileageService mileageService) {
        this.mileageService = mileageService;
    }

    /**
     * 查询区间有效里程；身份、失败状态和媒体类型遵循本入口的 OpenAPI 契约。
     *
     * @param startTime 上海时区区间起点，包含该时刻
     * @param endTime 上海时区区间终点，包含该时刻
     * @param robotIds 机器人标识集合
     * @return 查询区间有效里程的接口响应
     */
    @GetMapping
    @Operation(operationId = "queryMileageSummary", summary = "查询区间有效里程",
            description = "按分钟桶统计，区间首尾均包含。时间采用 Asia/Shanghai，接受本地展示格式和 ISO 本地时间。"
                    + "未指定或过滤后为空的 robotIds 查询全部机器人；空白项移除，重复项去重。"
                    + "此方法不校验用户身份，需由网络边界和 BFF 控制访问；无有效样本返回 null，不补零。")
    @ApiResponse(responseCode = "200", description = "查询成功，包括没有有效样本的情况",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MileageSummaryResponse.class)))
    @ApiResponse(responseCode = "400", description = "缺少时间参数、转换失败，或结束时间早于开始时间",
            content = @Content(mediaType = "application/json", schema = @Schema(oneOf = {
                    MileageErrorSchemas.ControlRequestError.class, MileageErrorSchemas.HttpRequestError.class})))
    @ApiResponse(responseCode = "500", description = "未处理的存储或服务错误",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MileageErrorSchemas.HttpRequestError.class)))
    public MileageSummaryResponse summary(
            @Parameter(description = "区间起点，上海时区；兼容 ISO 本地时间如 2026-08-14T00:00:00",
                    schema = @Schema(type = "string", format = "", example = "2026-08-14 00:00:00"))
            @RequestParam LocalDateTime startTime,
            @Parameter(description = "区间终点，上海时区；不得早于起点",
                    schema = @Schema(type = "string", format = "", example = "2026-08-14 23:59:59"))
            @RequestParam LocalDateTime endTime,
            @Parameter(description = "机器人列表；支持重复查询参数及单个逗号分隔值；不传或仅空白值时查询全部")
            @RequestParam(required = false) List<String> robotIds) {
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("里程统计结束时间不能早于开始时间");
        }
        return mileageService.summary(startTime, endTime, robotIds);
    }
}
