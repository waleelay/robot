package com.robot.control.config;

import com.robot.control.api.ControlFileController;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 导出 Control 文件及控制视频分组，保留默认里程文档的路径范围。 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class FileOpenApiConfig {
    /**
     * 生成 Control 文件代理分组，复用共享字段及当前身份和错误语义。
     *
     * @return 文件代理 OpenAPI 分组
     */
    @Bean
    public GroupedOpenApi controlFileOpenApi() {
        return GroupedOpenApi.builder().group("files")
                .pathsToMatch("/api/control/files", "/api/control/files/**")
                .addOpenApiMethodFilter(method -> method.getDeclaringClass() == ControlFileController.class)
                .addOpenApiCustomizer(api -> api
                        .info(new Info().title("Control 文件代理接口").version("1.0.0")
                                .description("除 HLS 播放 token 入口外，默认要求受信任的 X-User-Id 和 X-Roles；"
                                        + "仅开发配置允许身份回退。浏览器由 BFF 验证 JWT 后注入身份，Control 不独立验签。"
                                        + "成功 DTO 与 Media 共享，业务错误正文及状态透传；错误响应头不保证透传。"))
                        .servers(List.of(new Server().url("/")))
                        .tags(List.of(new Tag().name("files").description("文件上传、访问与处理进度")))
                        .security(List.of()))
                .addOpenApiCustomizer(api -> {
                    api.getComponents().addSecuritySchemes("TrustedUser", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-User-Id")
                            .description("由可信 BFF 注入，默认不能为空；不是独立签名凭据"));
                    api.getComponents().addSecuritySchemes("TrustedRoles", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Roles")
                            .description("由可信 BFF 注入的角色，默认不能为空，逗号分隔"));
                    api.getComponents().addSecuritySchemes("PlaybackToken", new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.QUERY).name("token")
                            .description("文件播放签名；不要求用户身份头"));
                    api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                            operation.setSecurity(List.of(path.contains("/hls/")
                                    ? new SecurityRequirement().addList("PlaybackToken")
                                    : new SecurityRequirement().addList("TrustedUser").addList("TrustedRoles")));
                            if (!path.contains("/hls/")) {
                                operation.addParametersItem(new HeaderParameter().name("X-Org-Id").required(false)
                                        .description("由可信 BFF 注入的组织 ID；缺省使用 control.auth.default-org-id")
                                        .schema(new StringSchema()));
                                operation.addParametersItem(new HeaderParameter().name("X-Client-Id").required(false)
                                        .description("终端标识，缺省为 web").schema(new StringSchema()));
                            }
                        }));
                })
                .build();
    }

    /**
     * 文件和里程之外的控制、视频及内部目录接口。
     *
     * @return 文件和里程以外的 Control 服务接口分组
     */
    @Bean
    public GroupedOpenApi controlServiceOpenApi() {
        return GroupedOpenApi.builder().group("service")
                .pathsToMatch("/api/control/**", "/internal/control/**")
                .pathsToExclude("/api/control/files", "/api/control/files/**", "/api/control/statistics/mileage")
                .addOperationCustomizer((operation, handler) -> {
                    String controller = handler.getBeanType().getSimpleName();
                    if (handler.getMethod().getName().equals("activeRecording")) {
                        operation.getResponses().get("200").setDescription("有活动录像时返回文件信息；没有活动录像时为 200 空正文");
                    }
                    describeControlMaps(operation);
                    operation.getResponses().values().forEach(response -> {
                        var content = response.getContent();
                        if (content != null && content.containsKey("application/json") && content.containsKey("*/*")
                                && content.get("application/json").getSchema() == null) {
                            content.put("application/json", content.remove("*/*"));
                        }
                    });
                    operation.setSecurity(List.of());
                    if (controller.startsWith("InternalFixedCamera")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("TrustedInternalCaller")));
                    } else if (java.util.Arrays.stream(handler.getMethodParameters()).anyMatch(p -> p.getParameterType() == jakarta.servlet.http.HttpServletRequest.class)) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("TrustedUser").addList("TrustedRoles")));
                        operation.addParametersItem(new HeaderParameter().name("X-Org-Id").required(false)
                                .description("可信用户组织，缺省取 control.auth.default-org-id").schema(new StringSchema()));
                        operation.addParametersItem(new HeaderParameter().name("X-Client-Id").required(false)
                                .description("可信终端标识，缺省为 web").schema(new StringSchema()));
                    }
                    for (String code : List.of("400", "403", "404", "409", "500", "503")) {
                        operation.getResponses().addApiResponse(code, new io.swagger.v3.oas.models.responses.ApiResponse()
                                .description("本地参数/权限/状态失败或下游错误；上游错误状态和正文保留，错误头不保证透传")
                                .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                                        new io.swagger.v3.oas.models.media.MediaType().schema(new io.swagger.v3.oas.models.media.ComposedSchema()
                                                .oneOf(List.of(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/ControlBusinessError"),
                                                        new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/FileBusinessError"),
                                                        new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/FrameworkError")))))));
                    }
                    return operation;
                })
                .addOpenApiCustomizer(api -> {
                    api.info(new Info().title("Control 控制与视频接口").version("1.0.0")
                            .description("控制、视频及内部生命周期契约；本服务信任受控上游身份，不独立验签浏览器 JWT。设备命令成功发布不等于设备已执行。"));
                    api.setServers(List.of(new Server().url("/")));
                    // OpenAPI 3.1 的 type:null 仍受 enum 约束；可空枚举必须显式包含 null。
                    api.getComponents().getSchemas().values().forEach(rawSchema -> {
                        io.swagger.v3.oas.models.media.Schema<?> schema = rawSchema;
                        if (schema.getProperties() != null) {
                            schema.getProperties().values().forEach(value -> {
                                io.swagger.v3.oas.models.media.Schema<?> field = (io.swagger.v3.oas.models.media.Schema<?>) value;
                                if (field.getTypes() != null && field.getTypes().contains("null") && field.getEnum() != null
                                        && !field.getEnum().contains(null)) {
                                    field.addEnumItemObject(null);
                                }
                            });
                        }
                    });
                    api.setTags(api.getPaths().values().stream().flatMap(p -> p.readOperations().stream())
                            .flatMap(o -> o.getTags().stream()).distinct().sorted().map(t -> new Tag().name(t).description("Control " + t + " 接口")).toList());
                    io.swagger.v3.core.converter.ModelConverters.getInstance(true).read(com.robot.media.common.file.FileHttpSchemas.FileBusinessError.class).forEach(api.getComponents()::addSchemas);
                    io.swagger.v3.core.converter.ModelConverters.getInstance(true).read(com.robot.media.common.file.FileHttpSchemas.FrameworkError.class).forEach(api.getComponents()::addSchemas);
                    // 本地异常处理器固定返回三字段；封闭模型可避免透传的 Media 错误同时命中两个 oneOf 分支。
                    api.getComponents().addSchemas("ControlBusinessError", schemaRequired(new io.swagger.v3.oas.models.media.ObjectSchema()
                            .description("本地参数或对讲占用错误；code 来自现有异常处理器")
                            .additionalProperties(false)
                            .addProperty("timestamp", new StringSchema().description("上海时区错误时间"))
                            .addProperty("code", new StringSchema().description("参数错误 INVALID_CONTROL_REQUEST 或对讲业务冲突码"))
                            .addProperty("message", new StringSchema().description("业务错误说明")), List.of("timestamp", "code", "message")));
                    api.getComponents().addSecuritySchemes("TrustedUser", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                            .name("X-User-Id").description("默认必填的可信用户 ID，开发回退需显式开启"));
                    api.getComponents().addSecuritySchemes("TrustedRoles", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                            .name("X-Roles").description("可信上游角色，按业务动作检查权限"));
                    api.getComponents().addSecuritySchemes("TrustedInternalCaller", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                            .name("X-Internal-Caller").description("目录租约默认要求 bigscreen-bff，发布生命周期默认要求 management-service，按配置匹配"));
                }).build();
    }
    /** 为动态控制载荷描述稳定字段，动作参数继续由设备专项协议定义。 */
    private void describeControlMaps(io.swagger.v3.oas.models.Operation operation) {
        String id = operation.getOperationId();
        var response = new io.swagger.v3.oas.models.media.ObjectSchema();
        switch (id) {
            case "controlRobotController_registry" -> {
                schemaRequired(response.description("运行时注册表；records 是机器人状态集合，具体状态字段随设备能力而异")
                        .addProperty("records", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("注册设备状态"), new io.swagger.v3.oas.models.media.ObjectSchema().additionalProperties(true)))
                        .addProperty("total", new io.swagger.v3.oas.models.media.IntegerSchema().description("返回设备数量")), List.of("records", "total"));
            }
            case "controlFixedCameraController_health" -> {
                schemaRequired(response.description("仅返回 Management 授权的摄像头；健康时间字符串为 RFC3339")
                        .addProperty("version", new StringSchema().description("健康快照协议版本"))
                        .addProperty("serverTime", new StringSchema().format("date-time").description("快照时间"))
                        .addProperty("records", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("摄像头、网关及码流的脱敏健康状态"), new io.swagger.v3.oas.models.media.ObjectSchema().additionalProperties(true))), List.of("version", "serverTime", "records"));
            }
            case "controlFixedCameraController_startVideos" -> {
                schemaRequired(response.description("摄像头去重后逐个启动；部分失败仍返回 HTTP 200，两组结果必须分别处理")
                        .addProperty("sessions", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("启动成功的摄像头"), new io.swagger.v3.oas.models.media.ObjectSchema().addProperty("cameraId", new StringSchema().description("摄像头 ID"))
                                        .addProperty("session", new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/VideoSessionResponse"))))
                        .addProperty("failures", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("启动失败的摄像头"), new io.swagger.v3.oas.models.media.ObjectSchema().addProperty("cameraId", new StringSchema().description("摄像头 ID"))
                                        .addProperty("message", new StringSchema().description("失败说明")))), List.of("sessions", "failures"));
            }
            default -> {
                if (!id.startsWith("controlRobotController_") || List.of("controlRobotController_startVideo", "controlRobotController_startIntercom").contains(id)) {
                    return;
                }
                response.description("装备控制业务结果；HTTP 200 也可能携带 CONTROL_LOCKED 或 ROBOT_STATE_CHANGED，调用方必须判断 code/status；PUBLISHED 只表示已发布")
                        .addProperty("robotId", new StringSchema().description("机器人 ID"))
                        .addProperty("controlSessionId", new StringSchema().description("控制租约 ID"))
                        .addProperty("commandId", new StringSchema().description("发布指令 ID"))
                        .addProperty("status", new StringSchema().description("控制租约或命令状态"))
                        .addProperty("code", new StringSchema().description("业务冲突编码；正常分支不包含"))
                        .addProperty("message", new StringSchema().description("业务冲突说明"))
                        .addProperty("holder", new io.swagger.v3.oas.models.media.ObjectSchema().description("当前控制权持有者"))
                        .addProperty("leaseExpireAt", new StringSchema().description("租约到期时间，上海时区 yyyy-MM-dd HH:mm:ss"))
                        .addProperty("issuedAt", new StringSchema().description("签发时间：普通命令为上海格式 yyyy-MM-dd HH:mm:ss；模式切换分支为 RFC3339"))
                        .addProperty("confirmToken", new StringSchema().description("短期确认标识；当前发布器未验证该字段，不应视为额外认证"))
                        .addProperty("expiresAt", new StringSchema().description("确认标识到期时间，上海时区 yyyy-MM-dd HH:mm:ss"))
                        .additionalProperties(true);
                if (operation.getRequestBody() != null) {
                    io.swagger.v3.oas.models.media.Schema<?> request = new io.swagger.v3.oas.models.media.ObjectSchema().description("按控制动作选择参数；沿用现有 Map 解析和默认行为")
                            .addProperty("deviceIds", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("控制租约覆盖的设备 ID；空集合的语义见控制协议"), new StringSchema()))
                            .addProperty("actions", schemaItems(new io.swagger.v3.oas.models.media.ArraySchema().description("申请的控制动作"), new StringSchema()))
                            .addProperty("scope", new StringSchema().description("申请范围，默认 DEVICE"))
                            .addProperty("observedStateSeq", new io.swagger.v3.oas.models.media.NumberSchema().description("客户端观察到的状态序号；接管和模式切换必需"))
                            .addProperty("controlSessionId", new StringSchema().description("当前用户终端拥有的有效控制租约"))
                            .addProperty("controlMode", new StringSchema().description("目标控制模式；按现有中文模式名及别名解析"))
                            .addProperty("target", new io.swagger.v3.oas.models.media.ObjectSchema().description("目标 scope/deviceId；具体设备必须在当前授权及租约范围内"))
                            .addProperty("action", new StringSchema().description("设备控制动作"))
                            .addProperty("params", new io.swagger.v3.oas.models.media.ObjectSchema().description("动作参数，按设备专项控制协议验证"))
                            .addProperty("client", new io.swagger.v3.oas.models.media.ObjectSchema().description("客户端指令元数据，seq 缺省为 0"))
                            .addProperty("fileId", new StringSchema().description("音频中转必填，必须属于当前机器人设备且已 READY，mp3/wav 不超过 20MB"))
                            .addProperty("reason", new StringSchema().description("释放原因，默认 user_release"))
                            .addProperty("stopActiveMotion", new io.swagger.v3.oas.models.media.BooleanSchema().description("释放时是否停止当前运动，默认 true"));
                    List<String> fields = switch (id) {
                        case "controlRobotController_acquireControl" -> List.of("scope", "deviceIds", "actions");
                        case "controlRobotController_takeoverControl" -> List.of("observedStateSeq");
                        case "controlRobotController_setControlMode" -> List.of("controlMode", "controlSessionId", "observedStateSeq");
                        case "controlRobotController_releaseControl" -> List.of("reason", "stopActiveMotion");
                        case "controlRobotController_confirmToken" -> List.of("target", "action");
                        case "controlRobotController_transferMultiFunctionAudioFile" -> List.of("fileId");
                        default -> List.of("controlSessionId", "target", "action", "params", "client");
                    };
                    request.getProperties().keySet().removeIf(field -> !fields.contains(field));
                    if (id.equals("controlRobotController_takeoverControl")) {
                        request.setRequired(List.of("observedStateSeq"));
                    } else if (id.equals("controlRobotController_setControlMode")) {
                        request.setRequired(List.of("controlMode", "controlSessionId", "observedStateSeq"));
                    } else if (id.equals("controlRobotController_transferMultiFunctionAudioFile")) {
                        request.setRequired(List.of("fileId"));
                    }
                    operation.getRequestBody().setContent(new io.swagger.v3.oas.models.media.Content()
                            .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType().schema(request)));
                }
            }
        }
        operation.getResponses().get("200").setContent(new io.swagger.v3.oas.models.media.Content()
                .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType().schema(response)));
    }

    private static io.swagger.v3.oas.models.media.Schema<?> schemaItems(io.swagger.v3.oas.models.media.Schema<?> schema, io.swagger.v3.oas.models.media.Schema<?> value) {
        schema.setItems(value);
        return schema;
    }

    private static io.swagger.v3.oas.models.media.Schema<?> schemaRequired(io.swagger.v3.oas.models.media.Schema<?> schema, List<String> value) {
        schema.setRequired(value);
        return schema;
    }

}
