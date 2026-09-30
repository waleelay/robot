package com.robot.bigscreen.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 描述 BFF 自有聚合和报告接口；通配代理独立引用下游，不生成虚假的星号业务路径。 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
    /**
     * 声明 BFF 自有接口的基础信息及固定响应模型。
     *
     * @return BFF OpenAPI 基础模型
     */
    @Bean
    public OpenAPI bffOpenApi() {
        return new OpenAPI().info(new Info().title("大屏 BFF 自有接口").version("1.0.0")
                        .description("JWT 在 BFF 验证；azp 受客户端白名单约束。全景与告警允许配置的现场客户端，其余自有 API 仅允许大屏客户端。"
                                + "聚合失败和缺测可能产生 null，不能当成零。通配代理契约来源见 x-proxy-contracts。"))
                .servers(List.of(new Server().url("/")))
                .schema("BadRequest", object("全景参数业务错误",
                        Map.of("success", flag("固定为 false"),
                                "code", text("固定为 BAD_REQUEST"), "message", text("错误说明"))))
                .schema("FrameworkError", object("请求解析、参数绑定失败或服务暂不可用时的框架错误；可随服务配置附加诊断字段",
                        Map.of("timestamp", text("错误发生时间"), "status", integer("HTTP 状态码"),
                                "error", text("HTTP 错误说明"), "path", text("发生错误的请求路径"))))
                .schema("RemovedApi", object("已移除入口的迁移提示",
                        Map.of("code", text("固定为 API_REMOVED"), "message", text("替代入口说明"))))
                .schema("AlarmDisposal", object("告警处置结果", Map.of(
                        "success", flag("下游处置是否成功"), "serverTime", text("上海时区服务时间"),
                        "alarmId", text("告警 ID"), "disposalStatus", text("处置状态编码"), "disposalStatusName", text("处置状态名称"),
                        "status", nullableText("成功后的告警状态；失败为 null"), "message", text("处置结果说明"))))
                .schema("ReportPage", object("当前用户可见报告分页", Map.of(
                        "data", array("报告记录，按创建时间倒序", object("报告记录", Map.of(
                                "id", text("报告 ID"), "reportName", text("报告名称"), "downloadTime", text("创建时间"),
                                "filename", text("下载文件名"), "format", text("文件格式"), "status", text("报告状态"), "statusName", text("报告状态名称")))),
                        "total", integer("总记录数"), "page", integer("页码，从 1 开始"), "size", integer("每页数量，归一化至 1 至 100"))));
    }

    /**
     * 补齐 BFF 自有聚合、权限及代理族引用，保持契约与当前响应一致。
     *
     * @return 对生成文档执行补充和规范化的定制器
     */
    @Bean
    public OpenApiCustomizer bffContract() {
        return api -> {
            bffOpenApi().getComponents().getSchemas().forEach(api.getComponents()::addSchemas);
            api.getComponents().addSchemas("CurrentAccess", currentAccessSchema());
            api.getComponents().addSecuritySchemes("BrowserJwt", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT").description("由 IAM 签发，BFF 验签并检查 azp；用户标识不接受浏览器伪造的受信头"));
            api.setSecurity(List.of(new SecurityRequirement().addList("BrowserJwt")));
            api.setTags(api.getPaths().values().stream().flatMap(path -> path.readOperations().stream())
                    .flatMap(operation -> operation.getTags().stream()).distinct().sorted()
                    .map(name -> new Tag().name(name).description("BFF " + name + " 接口")).toList());
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                // RequestMapping 未限定 method 的旧入口会展开为多种 HTTP 方法，分别使用稳定标识。
                if (path.equals("/api/control/robots")) {
                    operation.setOperationId("removedRobots" + method.name());
                    operation.getResponses().put("410", response("旧入口已移除", ref("RemovedApi")));
                }
                operation.getResponses().values().forEach(response -> {
                    Content content = response.getContent();
                    if (content != null && content.containsKey("application/json") && content.containsKey("*/*")
                            && content.get("application/json").getSchema() == null) {
                        content.put("application/json", content.remove("*/*"));
                    }
                });
                operation.getResponses().put("401", new ApiResponse().description("JWT 缺失或校验失败，安全过滤链可返回空正文"));
                operation.getResponses().put("403", new ApiResponse().description("JWT 的客户端标识不在该路由白名单，安全过滤链可返回空正文"));
                if (path.contains("/panorama/")) {
                    operation.getResponses().put("400", response("参数业务校验、请求解析或参数绑定失败",
                            new ComposedSchema().oneOf(List.of(ref("BadRequest"), ref("FrameworkError")))));
                }
                if (path.equals("/api/bigscreen/access-control/me")) {
                    operation.getResponses().put("200", response("原样透传 Management 当前访问上下文；模型来自已核对的源码，未代表在线联调验收", ref("CurrentAccess")));
                    operation.addExtension("x-upstream-source", "eiop@38f67fbe985fa5f0417ed4d18199216b154329e9: AccessUserController.current, CurrentAccessResponse, ApiResponse, JacksonConfig");
                }
                Schema<?> schema = responseSchema(operation.getOperationId());
                if (schema != null) {
                    operation.getResponses().put("200", response("处理完成；聚合数据质量与可空字段须由调用方判断", schema));
                }
                if (path.endsWith("/handled") || path.endsWith("/handle-and-continue")) {
                    operation.getRequestBody().setContent(new Content().addMediaType("application/json", new MediaType().schema(
                            new io.swagger.v3.oas.models.media.ObjectSchema().description("普通与工作流告警使用相同处置参数")
                                    .addProperty("disposalStatus", text("必填；IMMEDIATE_DISPOSAL 或 FALSE_ALARM，大小写不敏感、去除首尾空格"))
                                    .addProperty("handleResult", nullableText("处置说明，可省略")))));
                }
                if (path.endsWith("/reports/export")) {
                    operation.getRequestBody().setContent(new Content().addMediaType("application/json", new MediaType().schema(
                            new io.swagger.v3.oas.models.media.ObjectSchema().description("报告筛选，缺省生成月度全部设备的默认模块")
                                    .addProperty("range", text("timeRange.type 未提供时使用，默认 month"))
                                    .addProperty("timeRange", new io.swagger.v3.oas.models.media.ObjectSchema().description("时间选择")
                                            .addProperty("type", text("today/week/month/all/custom；其他值按 month 归一化"))
                                            .addProperty("startTime", text("custom 起始时间，上海时区"))
                                            .addProperty("endTime", text("custom 结束时间，上海时区")))
                                    .addProperty("deviceType", text("设备类型编码，默认 all"))
                                    .addProperty("modules", array("报告模块；省略或空集合采用默认五个模块", text("equipmentRuntime/aiAlarmAnalysis/alarmAreaRanking/alarmTrend/taskCompletion"))))));
                }
                if (List.of("statisticsController_overview", "statisticsController_exportReport")
                        .contains(operation.getOperationId())) {
                    operation.getResponses().put("503", response("统计查询执行器已满或正在停止；调用方稍后重试",
                            ref("FrameworkError")));
                }
                if (path.contains("/reports/{id}")) {
                    operation.getResponses().put("404", new ApiResponse().description("当前用户范围内不存在该报告，无正文"));
                }
            }));
            api.addExtension("x-proxy-contracts", List.of(
                    Map.of("source", "/api/control/**", "target", "/api/control/**", "contracts", List.of("control-mileage.json", "control-files.json", "control-service.json")),
                    Map.of("source", "/api/media/**", "target", "/api/media/**", "contracts", List.of("media-files.json", "media-service.json")),
                    Map.of("source", "/api/bigscreen/**", "target", "见 CenterProxyClient.targetPath；自有 Controller 优先", "contracts", List.of("control-files.json", "control-service.json")),
                    Map.of("source", "/api/manage/**", "target", "/api/manage/**", "coverage", "外部 Management，尚无经验证的 OpenAPI 导出物"),
                    Map.of("source", "/api/v1/management/**", "target", "/api/v1/management/**", "coverage", "外部 Management，尚无经验证的 OpenAPI 导出物"),
                    Map.of("source", "/api/bigscreen/business/**", "target", "BusinessTaskProxyController.targetPath 显式分支", "coverage", "外部 Management，映射和身份回归已纳入测试")));
        };
    }

    /**
     * 按稳定操作标识选择 BFF 固定聚合模型；动态上游扩展继续保留，不强行推断未知业务字段。
     */
    private Schema<?> responseSchema(String id) {
        return switch (id) {
            case "panoramaController_mountedDeviceCount" -> object("设备挂载组件数量；未找到时可能为空", Map.of(
                    "robotId", nullableText("设备 ID"), "mountedDeviceCount", schemaTypes(new IntegerSchema(), java.util.Set.of("integer", "null")).description("组件数量，缺测为 null")));
            case "panoramaController_handleAlarm", "panoramaController_handleWorkflowAlarm" -> ref("AlarmDisposal");
            case "statisticsController_reportHistoryList" -> ref("ReportPage");
            case "panoramaController_overview" -> object("全景聚合；动态子模型继续遵循全景协议和 Management 字段", Map.ofEntries(
                    Map.entry("serverTime", text("上海时区服务时间")), Map.entry("devices", dynamicArray("设备摘要")),
                    Map.entry("deviceStats", deviceStats()), Map.entry("deviceTypeStats", array("设备分类统计", object("分类统计", Map.of(
                            "type", text("类型编码"), "name", text("类型名称"), "count", integer("设备数量"),
                            "fault", integer("故障数量"), "offline", integer("离线数量"))))),
                    Map.entry("patrolOverview", object("巡检与里程指标", Map.of(
                            "durationToday", nullableNumber("今日累计时长，小时；无有效时长为 null"),
                            "durationUnit", nullableText("有有效时长时为 小时"),
                            "mileageToday", nullableNumber("今日里程，千米；缺测为 null，真实零值为 0"),
                            "mileageUnit", nullableText("有里程数据时为 KM"), "mileageHasData", flag("是否有有效里程样本")))),
                    Map.entry("tasks", dynamicArray("任务摘要")),
                    Map.entry("taskOverview", object("任务统计", Map.of("totalToday", integer("今日任务数"),
                            "completedRateText", nullableText("当前尚无有效完成率"), "running", integer("执行中数量"), "pending", integer("等待中数量")))),
                    Map.entry("dataQuality", dataQuality("tasks", "alarms")),
                    Map.entry("map", dynamicArray("地图摘要")), Map.entry("alarms", dynamic("按等级分组的告警，降级时可为空对象"))));
            case "panoramaController_mapResources" -> object("地图资源；fixedCamares 为既有协议拼写", Map.of(
                    "serverTime", text("服务时间"), "mapId", text("地图 ID"), "points", dynamicArray("地图点位"), "fixedCamares", dynamicArray("地图固定摄像头")));
            case "panoramaController_mapTaskRoutes" -> object("地图任务路线", Map.of(
                    "serverTime", text("服务时间"), "mapId", text("地图 ID"), "items", dynamicArray("任务 ID、实例 ID 及路线 segments"), "dataQuality", dataQuality("tasks")));
            case "panoramaController_taskDetail" -> object("任务详情；找不到时 task 为 null", Map.of(
                    "serverTime", text("服务时间"), "task", schemaTypes(new io.swagger.v3.oas.models.media.ObjectSchema(), java.util.Set.of("object", "null")).description("任务计划及运行实例"), "dataQuality", dataQuality("tasks")));
            case "panoramaController_taskFixedCameras" -> object("任务固定摄像头；不含推流地址或凭据", Map.of(
                    "serverTime", text("服务时间"), "taskId", text("任务 ID"), "items", array("安全的视频源摘要", object("固定摄像头摘要", Map.of(
                            "cameraId", text("摄像头 ID"), "name", nullableText("摄像头名称"), "sourceType", text("固定为 FIXED_CAMERA"),
                            "sourceId", text("与 cameraId 相同"), "defaultQuality", text("存在子码流为 sub，否则 main"))))));
            case "panoramaController_tasks" -> object("任务列表", Map.of("serverTime", text("服务时间"), "total", integer("本次返回条数"),
                    "items", dynamicArray("任务摘要"), "dataQuality", dataQuality("tasks")));
            case "panoramaController_alarms" -> object("告警聚合", Map.of("serverTime", text("服务时间"), "alarms", dynamic("按等级分组的告警"), "dataQuality", dataQuality("alarms")));
            case "panoramaController_alarmPage" -> object("告警分页", Map.of("serverTime", text("服务时间"), "total", integer("总数"), "pageNum", integer("页码，从 1 开始"),
                    "pageSize", integer("每页数量"), "items", dynamicArray("告警详情")));
            case "panoramaController_actionableWorkflowAlarms" -> object("可处置的工作流告警", Map.of("serverTime", text("服务时间"), "total", integer("总数"), "items", dynamicArray("可处置告警")));
            case "statisticsController_overview" -> statisticsOverview();
            default -> null;
        };
    }

    private Schema<?> deviceStats() {
        return object("设备统计", Map.of("total", integer("设备总数"), "online", integer("在线数量"),
                "fault", integer("故障数量"), "offline", integer("离线数量")));
    }

    private Schema<?> dataQuality(String... parts) {
        Map<String, Schema<?>> fields = new java.util.LinkedHashMap<>();
        for (String part : parts) {
            Schema<?> quality = object("数据完整性；降级不等于真实空集合", Map.of(
                    "complete", flag("数据是否完整"), "degraded", flag("是否降级"),
                    "reasonCodes", array("失败原因编码", text("原因编码"))));
            if ("tasks".equals(part)) {
                quality.addProperty("invalidReferenceCount", integer("失效工作流引用数量"));
                quality.addProperty("invalidWorkflowReferences", array("失效工作流引用", text("引用标识")));
                quality.setRequired(List.of("complete", "degraded", "invalidReferenceCount", "invalidWorkflowReferences", "reasonCodes"));
            }
            fields.put(part, quality);
        }
        return object("各数据源的质量状态", fields);
    }

    private Schema<?> statisticsOverview() {
        Schema<?> kpi = object("指标与环比；未知值为 null", Map.of(
                "value", nullableNumber("指标值，里程单位千米，其余数量或百分比"),
                "compareRate", nullableNumber("环比百分比；无可比样本或上期为零且本期非零时为 null")));
        Schema<?> ranking = array("数量降序分布", object("分布项", Map.of(
                "name", text("分类名称"), "count", integer("数量"), "percent", number("占比百分数"))));
        return object("统计聚合", Map.ofEntries(
                Map.entry("serverTime", text("上海时区服务时间")),
                Map.entry("range", object("实际统计起止时间，上海时区", Map.of(
                        "type", text("today/week/month/all/custom"), "startTime", text("包含的开始时间"), "endTime", text("包含的结束时间")))),
                Map.entry("deviceTypeOptions", array("设备类型选项", object("类型选项", Map.of(
                        "value", text("类型编码，all 表示全部"), "label", text("显示名称"))))),
                Map.entry("filters", object("生效筛选条件", Map.of(
                        "deviceType", text("设备类型编码"), "areaId", nullableText("请求中的区域 ID；当前统计实现未按该值过滤")))),
                Map.entry("kpis", object("统计指标", Map.of("taskTotal", kpi, "patrolMileage", kpi,
                        "aiAlarmTotal", kpi, "autoHandleSuccessRate", kpi))),
                Map.entry("equipmentRuntime", object("设备运行统计", Map.of(
                        "onlineRate", nullableNumber("当前在线率，百分数；无设备为 null"),
                        "taskCompletionRate", nullableNumber("任务完成率，百分数；无任务为 null"),
                        "unit", nullableText("有运行时长分类时为 小时"),
                        "items", array("各类型的采样折算时长", object("类型时长", Map.of(
                                "deviceType", text("类型编码"), "deviceTypeName", text("类型名称"),
                                "runningHours", number("运行小时数"), "faultHours", number("故障小时数"),
                                "offlineHours", number("离线小时数"))))))),
                Map.entry("aiAlarmAnalysis", object("AI 告警分析", Map.of(
                        "alarmTypeRanking", ranking, "handleMethodRanking", ranking))),
                Map.entry("alarmAreaRanking", array("前十个告警区域", object("区域统计", Map.of(
                        "areaId", nullableText("当前未提供区域 ID"), "areaName", text("区域名称"),
                        "count", integer("告警数量"), "percent", number("占比百分数"))))),
                Map.entry("alarmTrend", object("告警时间趋势", Map.of("unit", nullableText("有效时间区间为 次"),
                        "points", array("按时间升序的采样点", object("趋势点", Map.of(
                                "label", text("按区间使用 HH:mm、MM.dd 或 yyyy-MM"), "count", integer("告警数量"))))))),
                Map.entry("taskCompletion", object("任务完成统计", Map.of("insight", nullableText("统计说明，无任务为 null"),
                        "items", array("任务状态分布", object("状态统计", Map.of(
                                "status", text("COMPLETED/RUNNING/PENDING/INTERRUPTED"), "name", text("状态名称"),
                                "count", integer("数量"), "percent", number("占比百分数")))))))));
    }

    private Schema<?> currentAccessSchema() {
        Schema<?> role = object("业务角色；Management 将 ID 序列化为字符串", Map.of("roleId", nullableText("角色 ID"),
                "roleCode", nullableText("角色编码"), "roleName", nullableText("角色名称"), "roleType", nullableText("角色类型")));
        Schema<?> data = object("当前访问上下文", Map.ofEntries(
                Map.entry("userId", text("用户 ID")), Map.entry("username", nullableText("用户名")),
                Map.entry("roles", array("业务角色", role)), Map.entry("permissions", array("有效权限编码", text("权限编码"))),
                Map.entry("organizationId", nullableText("主组织 ID，Management 将 Long ID 序列化为字符串")),
                Map.entry("organizationCode", nullableText("主组织编码")), Map.entry("organizationName", nullableText("主组织名称")),
                Map.entry("managementOrganizationIds", array("可管理组织 ID 集合", text("组织 ID 字符串"))),
                Map.entry("emergencyAdministrator", flag("兼容字段，当前固定 false")),
                Map.entry("authorizationBypassed", flag("是否关闭业务授权限制，仍需有效登录")),
                Map.entry("initializationStatus", text("PENDING 或 COMPLETED")), Map.entry("initializer", flag("是否为初始化人员")),
                Map.entry("initializerDisabled", flag("初始化账号是否停用"))));
        return object("Management 统一响应封装，BFF 原样透传", Map.of("code", text("成功为 0"), "message", text("响应说明"),
                "traceId", text("追踪 ID，可为空字符串"), "data", data));
    }

    private static Schema<?> flag(String description) { return new BooleanSchema().description(description); }
    private static Schema<?> text(String description) { return new StringSchema().description(description); }
    private static Schema<?> nullableText(String description) { return schemaTypes(new StringSchema(), java.util.Set.of("string", "null")).description(description); }
    private static Schema<?> integer(String description) { return new IntegerSchema().description(description); }
    private static Schema<?> number(String description) { return new io.swagger.v3.oas.models.media.NumberSchema().description(description); }
    private static Schema<?> nullableNumber(String description) {
        return schemaTypes(number(description), java.util.Set.of("number", "null"));
    }
    private static Schema<?> dynamic(String description) { return new io.swagger.v3.oas.models.media.ObjectSchema().description(description).additionalProperties(true); }
    private static Schema<?> dynamicArray(String description) { return array(description, dynamic(description + "条目，字段由上游协议定义")); }
    private static Schema<?> array(String description, Schema<?> item) { return schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description(description), item); }
    private static Schema<?> ref(String name) { return new Schema<>().$ref("#/components/schemas/" + name); }
    private static Schema<?> object(String description, Map<String, Schema<?>> fields) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(description);
        fields.forEach(schema::addProperty);
        schema.setRequired(fields.keySet().stream().sorted().toList());
        return schema;
    }
    private static ApiResponse response(String description, Schema<?> schema) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }
    private static io.swagger.v3.oas.models.media.Schema<?> schemaItems(io.swagger.v3.oas.models.media.Schema<?> schema, io.swagger.v3.oas.models.media.Schema<?> value) {
        schema.setItems(value);
        return schema;
    }

    private static io.swagger.v3.oas.models.media.Schema<?> schemaTypes(io.swagger.v3.oas.models.media.Schema<?> schema, java.util.Set<String> value) {
        schema.setTypes(new java.util.TreeSet<>(value));
        return schema;
    }

}
