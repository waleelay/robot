package com.robot.control.mileage;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 描述现有异常处理器和 Spring Boot 默认错误响应，不改变异常处理行为。
 *
 * @author Codex
 * @date 2026-09-29
 */
public final class MileageErrorSchemas {

    private MileageErrorSchemas() {
    }

    /**
     * 非法时间区间由 Control 的 IllegalArgumentException 处理器返回。
     *
     * @param timestamp 上海时区错误时间
     * @param code 错误码
     * @param message 错误说明
     */
    @Schema(description = "Control 参数业务校验失败")
    public record ControlRequestError(
            @Schema(description = "上海时区错误时间", requiredMode = Schema.RequiredMode.REQUIRED) String timestamp,
            @Schema(description = "错误码", allowableValues = "INVALID_CONTROL_REQUEST",
                    requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(description = "错误说明", requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }

    /**
     * 缺少参数、参数转换失败或未处理异常由 Spring Boot 默认错误入口返回。
     *
     * @param timestamp 错误时间
     * @param status HTTP 状态码
     * @param error HTTP 错误名称
     * @param path 请求路径
     */
    @Schema(description = "Spring Boot 默认 JSON 错误响应；附加字段由部署配置控制")
    public record HttpRequestError(
            @Schema(description = "错误时间", requiredMode = Schema.RequiredMode.REQUIRED) String timestamp,
            @Schema(description = "HTTP 状态码", requiredMode = Schema.RequiredMode.REQUIRED) int status,
            @Schema(description = "HTTP 错误名称", requiredMode = Schema.RequiredMode.REQUIRED) String error,
            @Schema(description = "请求路径", requiredMode = Schema.RequiredMode.REQUIRED) String path) {
    }
}
