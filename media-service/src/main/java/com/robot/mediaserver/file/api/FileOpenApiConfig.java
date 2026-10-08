package com.robot.mediaserver.file.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 导出 Media 文件及视频语音分组，保留公开和内部别名的稳定操作标识。 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class FileOpenApiConfig {
    /**
     * 声明 Media 文件接口的文档信息、身份边界及响应模型。
     *
     * @return 文件接口 OpenAPI 基础模型
     */
    @Bean
    public OpenAPI fileOpenApi() {
        return new OpenAPI().info(new Info().title("Media 文件接口").version("1.0.0")
                        .description("Media 仅供受控网络调用。用户接口信任上游身份 Header，解析器仍保留开发默认值；"
                                + "机器人分片接口依赖 X-Robot-Id 及公开路径的来源网络过滤；HLS 使用播放 token；"
                                + "MinIO 回调使用内部 Bearer 凭证。不同入口不能套用统一用户 JWT 鉴权。"))
                .servers(List.of(new Server().url("/")))
                .tags(List.of(new Tag().name("files").description("文件上传、访问与处理进度")))
                .security(List.of());
    }

    /**
     * 为文件公开与内部别名分配稳定操作标识，并过滤默认文档到文件范围。
     *
     * @return 文件接口文档定制器
     */
    @Bean
    public OpenApiCustomizer fileAliasOperationIds() {
        return api -> {
            api.getPaths().keySet().removeIf(path -> !(path.equals("/api/media/files") || path.startsWith("/api/media/files/")
                    || path.equals("/internal/media/files") || path.startsWith("/internal/media/files/")
                    || path.equals("/internal/media/file-upload-events/minio")));
            new ReferencedSchemas().prune(api);
            api.getComponents().addSecuritySchemes("PlaybackToken", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.QUERY).name("token")
                    .description("文件播放签名，绑定文件及有效期；不是用户 JWT"));
            api.getComponents().addSecuritySchemes("RobotId", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Robot-Id")
                    .description("受控机器人标识，非独立签名凭据；公开路径还受来源网络配置限制"));
            api.getComponents().addSecuritySchemes("MinioWebhookToken", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .description("配置的内部回调凭证，使用常量时间比较；不是用户 JWT"));
            api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                String id = operation.getOperationId().replaceFirst("_\\d+$", "");
                operation.setOperationId(id + (path.startsWith("/internal/") ? "Internal" : "Public"));
                if (path.contains("/hls/")) {
                    operation.setSecurity(List.of(new SecurityRequirement().addList("PlaybackToken")));
                } else if (path.contains("multipart-uploads") || path.endsWith("/status")) {
                    operation.setSecurity(List.of(new SecurityRequirement().addList("RobotId")));
                } else if (path.endsWith("file-upload-events/minio")) {
                    operation.setSecurity(List.of(new SecurityRequirement().addList("MinioWebhookToken")));
                } else if (!path.endsWith("upload-progress-queries")) {
                    operation.addParametersItem(new HeaderParameter().name("X-User-Id").required(false)
                            .description("受控上游用户 ID；当前解析器缺省为开发身份，必须依赖网络隔离")
                            .schema(new StringSchema()));
                    operation.addParametersItem(new HeaderParameter().name("X-Roles").required(false)
                            .description("受控上游角色，逗号分隔；当前解析器保留开发默认角色")
                            .schema(new StringSchema()));
                    operation.addParametersItem(new HeaderParameter().name("X-Org-Id").required(false)
                            .description("受控上游组织 ID；缺省使用 media.file.default-org-id")
                            .schema(new StringSchema()));
                    operation.addParametersItem(new HeaderParameter().name("X-Client-Id").required(false)
                            .description("终端标识，缺省为 web")
                            .schema(new StringSchema()));
                }
            }));
        };
    }

    /**
     * 视频、固定摄像头、现场呼叫和语音接口独立分组，复用原有文件契约。
     *
     * @return 文件以外的 Media 服务接口分组
     */
    @Bean
    public org.springdoc.core.models.GroupedOpenApi mediaServiceOpenApi() {
        return org.springdoc.core.models.GroupedOpenApi.builder().group("service")
                .pathsToMatch("/internal/media/**", "/api/media/tts/**")
                .pathsToExclude("/api/media/files", "/api/media/files/**", "/internal/media/files", "/internal/media/files/**", "/internal/media/file-upload-events/minio")
                .addOperationCustomizer((operation, handler) -> {
                    String controller = handler.getBeanType().getSimpleName();
                    if (handler.getMethod().getName().equals("activeRecording")) {
                        operation.getResponses().get("200").setDescription("有活动录像时返回文件信息；没有活动录像时为 200 空正文");
                    }
                    if (List.of("expireIntercom", "releaseIdle").contains(handler.getMethod().getName())) {
                        var payload = new io.swagger.v3.oas.models.media.ObjectSchema().description("无需停止发布者时为空对象；否则返回待下发的停止指令，不代表已完成设备停止")
                                .addProperty("robotId", new StringSchema().description("目标机器人或 Gateway ID"))
                                .addProperty("sessionId", new StringSchema().description("视频会话 ID"))
                                .addProperty("commandId", new StringSchema().description("本次生成的指令 ID"))
                                .addProperty("roomName", new StringSchema().description("LiveKit 房间名"));
                        if (handler.getMethod().getName().equals("releaseIdle")) {
                            payload.addProperty("sourceType", new StringSchema().description("来源类型"))
                                    .addProperty("sourceId", new StringSchema().description("来源 ID"))
                                    .addProperty("deviceId", new StringSchema().description("摄像头组件 ID"));
                        }
                        operation.getResponses().get("200").setContent(new io.swagger.v3.oas.models.media.Content()
                                .addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType().schema(payload)));
                    }
                    if (handler.getMethod().getName().equals("generateFile")) {
                        operation.getResponses().get("200")
                                .addHeaderObject("X-TTS-Cache-Hit", new io.swagger.v3.oas.models.headers.Header().description("是否复用已生成语音文件").schema(new StringSchema()))
                                .addHeaderObject("Content-Disposition", new io.swagger.v3.oas.models.headers.Header().description("语音文件下载名称").schema(new StringSchema()));
                    }
                    operation.getResponses().values().forEach(response -> {
                        var content = response.getContent();
                        if (content != null && content.containsKey("application/json") && content.containsKey("*/*")
                                && content.get("application/json").getSchema() == null) {
                            content.put("application/json", content.remove("*/*"));
                        }
                    });
                    operation.setSecurity(List.of());
                    if (controller.equals("FixedCameraSourceController")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("TrustedInternalCaller")));
                    } else if (controller.equals("LiveKitWebhookController")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("LiveKitSignature")));
                        operation.getResponses().addApiResponse("400", new io.swagger.v3.oas.models.responses.ApiResponse().description("回调载荷无效，无正文"));
                        operation.getResponses().addApiResponse("401", new io.swagger.v3.oas.models.responses.ApiResponse().description("回调签名无效，无正文"));
                        return operation;
                    } else if (controller.equals("RobotTtsController")) {
                        operation.setSecurity(List.of(new SecurityRequirement().addList("RobotId")));
                    }
                    for (String code : List.of("400", "403", "404", "409", "500", "503")) {
                        operation.getResponses().addApiResponse(code, new io.swagger.v3.oas.models.responses.ApiResponse()
                                .description("参数、权限、状态冲突或依赖故障；按实际业务/框架错误结构返回")
                                .content(new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                                        new io.swagger.v3.oas.models.media.MediaType().schema(new io.swagger.v3.oas.models.media.ComposedSchema()
                                                .oneOf(List.of(new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/FileBusinessError"),
                                                        new io.swagger.v3.oas.models.media.Schema<>().$ref("#/components/schemas/FrameworkError")))))));
                    }
                    if (java.util.Arrays.stream(handler.getMethodParameters()).anyMatch(p -> p.getParameterType() == jakarta.servlet.http.HttpServletRequest.class)
                            && !controller.equals("FixedCameraSourceController")) {
                        operation.addParametersItem(new HeaderParameter().name("X-User-Id").required(false)
                                .description("可信上游用户 ID；当前 Media 解析器保留开发默认身份，必须依赖网络隔离").schema(new StringSchema()));
                        operation.addParametersItem(new HeaderParameter().name("X-Roles").required(false)
                                .description("可信上游角色，逗号分隔；媒体查看、操作及对讲权限按业务方法校验").schema(new StringSchema()));
                    }
                    return operation;
                })
                .addOpenApiCustomizer(api -> {
                    api.info(new Info().title("Media 视频与语音接口").version("1.0.0")
                            .description("文件以外的 Media HTTP 契约。内部接口必须由部署网络隔离；回调签名、内部调用者和用户头分别描述。HTTP 成功不等于轨道就绪或录像后处理完成。"));
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
                            .flatMap(o -> o.getTags().stream()).distinct().sorted().map(t -> new Tag().name(t).description("Media " + t + " 接口")).toList());
                    io.swagger.v3.core.converter.ModelConverters.getInstance(true).read(com.robot.media.common.file.FileHttpSchemas.FileBusinessError.class).forEach(api.getComponents()::addSchemas);
                    io.swagger.v3.core.converter.ModelConverters.getInstance(true).read(com.robot.media.common.file.FileHttpSchemas.FrameworkError.class).forEach(api.getComponents()::addSchemas);
                    api.getComponents().addSecuritySchemes("TrustedInternalCaller", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER).name("X-Internal-Caller").description("配置的可信调用者，默认 control-service；该 Header 必须由受控服务注入"));
                    api.getComponents().addSecuritySchemes("LiveKitSignature", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                            .description("LiveKit 回调 JWT 签名及正文摘要校验，不是浏览器用户 JWT"));
                    api.getComponents().addSecuritySchemes("RobotId", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER).name("X-Robot-Id").description("受控机器人标识，TTS 路径不独立验证用户 JWT"));
                }).build();
    }
    /** 过滤默认文件路径后同步移除其他分组的模型，确保完整应用与隔离测试导出一致。 */
    private static final class ReferencedSchemas extends io.swagger.v3.core.filter.SpecFilter {
        void prune(OpenAPI api) {
            removeBrokenReferenceDefinitions(api);
        }
    }

}
