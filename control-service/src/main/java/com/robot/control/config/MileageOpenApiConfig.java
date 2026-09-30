package com.robot.control.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 仅导出里程查询试点契约；生产环境默认关闭文档端点。
 *
 * @author Codex
 * @date 2026-09-29
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class MileageOpenApiConfig {

    /**
     * 默认文档仍只公开里程路径；文件接口通过独立 files 分组提供。
     *
     * @return 限定里程默认文档并清理无引用模型的定制器
     */
    @Bean
    public OpenApiCustomizer mileageDocumentPaths() {
        return api -> {
            api.getPaths().keySet().removeIf(path -> !"/api/control/statistics/mileage".equals(path));
            new ReferencedSchemas().prune(api);
            java.util.Set<String> usedTags = api.getPaths().values().stream().flatMap(path -> path.readOperations().stream())
                    .flatMap(operation -> operation.getTags().stream()).collect(java.util.stream.Collectors.toSet());
            api.setTags(api.getTags().stream().filter(tag -> usedTags.contains(tag.getName())).toList());
        };
    }

    /**
     * 声明 Control 内网查询的实际信任边界。
     *
     * @return 使用相对地址的试点接口契约
     */
    @Bean
    public OpenAPI mileageOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Control 里程统计查询").version("1.0.0")
                        .description("仅覆盖里程统计查询。Control 此方法不独立验签或解析用户身份；"
                                + "仅允许受控内网调用。浏览器访问 BFF 时由 BFF 验证 JWT。"))
                .servers(List.of(new Server().url("/")))
                .security(List.of());
    }
    /** 默认文档只保留里程接口可达的 Schema，避免完整应用加载其他 Controller 后快照漂移。 */
    private static final class ReferencedSchemas extends io.swagger.v3.core.filter.SpecFilter {
        void prune(OpenAPI api) {
            removeBrokenReferenceDefinitions(api);
        }
    }

}
