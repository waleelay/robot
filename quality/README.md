# 开发规范自动检查

本目录维护 Alibaba Java 规则映射，以及三个 Java 服务现有 HTTP 入口的 OpenAPI 契约。
规范来源、人工审查及存量处理要求见[开发规范](../docs/02-设计/工程规范/Alibaba与OpenAPI开发规范.md)。
执行证据见[规范整改执行记录](../docs/04-测试与验收/执行记录/规范整改执行记录-20260929.md)。
存量问题及首批处置见[整改台账](../docs/02-设计/工程规范/存量规范整改台账-20260929.md)，审查快照不参与规则豁免。

## 1. 运行方式

环境：JDK 17、Maven 3.9+、Python 3.9+、Node.js 18+ 和 npm。首次运行需要下载工具依赖并写入 Maven/npm 缓存。
HTTP 契约测试在本机随机端口运行。里程测试使用 H2；文件入口测试替换 Media 业务服务，Control 使用真实 HTTP 客户端配合模拟下游，不连接外部 MySQL、MinIO、Redis、MQTT、LiveKit 或 Management。

从仓库根目录执行：

```bash
# 全量 Java 检查，现有问题也会导致失败，不默认豁免。
sh scripts/quality-check.sh java

# 对比明确的 Git 基线，扫描全部 Java 生产/测试源码并阻断新增问题。
# HEAD 只适合检查当前未提交修改；CI 必须传入合并目标的确定提交。
sh scripts/quality-check.sh java --base HEAD

# 三个服务的契约生成一致性、HTTP 响应校验、OpenAPI lint 和调用方回归。
sh scripts/quality-check.sh openapi

# 组合执行；已有依赖缓存时可验证离线模式。
sh scripts/quality-check.sh all --base HEAD --offline
```

退出码：`0` 表示所选检查通过；`1` 表示 Java 全量或增量规则发现问题；`2` 表示检查流程、依赖、编译、测试或其他工具失败。
不存在规则文件、缺少契约、解析失败、规则异常或空扫描范围都不会被当成通过。

报告在 `target/quality/`，Maven 报告在模块的 `target/surefire-reports/`，依赖缓存和报告不提交。
契约和响应样例是有意版本管理的接口审查产物。

## 2. Java 检查范围与覆盖限制

固定 PMD CLI/Java `7.17.0`，构建插件版本见 [java/pom.xml](java/pom.xml)。
扫描 `media-common`、`backend`、`control-service`、`bigscreen-bff` 的 `src/main/java` 与 `src/test/java`。
先安装共享 DTO，编译模块的主代码和测试代码，生成实际辅助 classpath，再运行检查；该编译步骤本身不执行全部业务测试。

| Alibaba 检查分类 | 实际 PMD 规则 | 首批覆盖 |
| --- | --- | --- |
| ALI-NAMING | ClassNamingConventions | 类型命名，包括 record；测试类保留 Test/Tests/TestCase、Test 前缀及 IT 后缀；不等于全部命名约束 |
| ALI-NAMING | MethodNamingConventions | 方法命名；保留工具对测试方法等类型的区别 |
| ALI-NAMING | LongSuffixUppercase（项目 XPath） | long 字面量大写 L 后缀 |
| ALI-STYLE | ControlStatementBraces | 控制语句大括号 |
| ALI-OOP | OverrideBothEqualsAndHashcode | equals/hashCode 成对覆盖；Comparable 等剩余场景人工审查 |
| ALI-OOP | AvoidDecimalLiteralsInBigDecimalConstructor | 避免用浮点字面量构造 BigDecimal；变量传参场景仍需审查 |
| ALI-EXCEPTION | ReturnFromFinallyBlock | finally 不返回结果覆盖原返回值或异常 |
| ALI-COMMENT | CommentRequired | 类、接口、枚举的 Javadoc |
| ALI-COMMENT | RecordCommentRequired（项目 Java 规则） | record 用途、全部组成字段的 `@param`，同时拒绝空说明、名称占位和已不存在的字段标签 |
| ALI-COMMENT | MemberDocumentationRequired（项目 Java 规则） | 公开/受保护字段、实体与配置类实例字段、带 Schema/Column/Value 的实例字段；枚举值、公开/受保护方法及显式构造器的 Javadoc；完整参数、返回值、声明异常及过期参数检查；40 行以上方法的职责说明 |

检查使用 [ruleset.xml](java/ruleset.xml)，共 10 项；它们是已验证映射，不是 Alibaba 全部规范的替代实现。
注释质量、短方法中的关键业务决策、作者信息、并发、日志、SQL、事务、权限及资源生命周期仍需人工审查。方法行数仅作为漏写说明的兜底线索，不代表复杂度或语义已被工具证明；覆盖父接口契约的 `@Override` 可复用其文档，新增约束或关键处理仍应另写说明。
完整[规则正反例](java/tests/test_quality_runner.py)在每次 Java 检查前执行，当前 17 项测试涵盖 Java 17 语法、全部启用规则、业务字段与嵌套配置、实现细节字段边界、枚举值、record 组成字段、公开/接口/构造方法、继承契约、声明异常、长方法、多行标签说明、空标签与过期参数、测试类 IT 后缀、解析失败、空扫描及增量比较。多行说明按块标签边界读取，空说明及仅重复参数名仍被拒绝。IT 适配修正工具默认命名与仓库集成测试约定的偏差，不修改 Maven 测试发现配置，也不豁免测试代码。字段规则不能识别所有缓存、锁或普通业务属性，不能替代逐成员语义审查。

增量模式从显式 Git 提交提取生产和测试源码，使用同一规则集扫描，与当前工作区按“文件、规则、违规代码内容指纹及重复次数”比较。
比较不依赖行号或告警总数；文件改名、违规片段改写可能触发重新审查。
辅助 classpath 使用当前编译结果；本批规则的覆盖边界如上。引入更复杂的跨版本类型分析规则前，需验证或分别构建基线 classpath。
当前没有持久化豁免文件，增量通过不等于存量已经整改。

## 3. OpenAPI 契约

- 覆盖仓库 136 条具体 HTTP 映射：Media 73、Control 44、BFF 19。BFF 的旧 ANY 入口展开后，共生成 142 个 HTTP 操作；6 个通配代理族单列引用，不伪装成完整外部契约。
- springdoc `2.8.17` 从 Controller、响应 DTO 和注解生成 OAS `3.1.0`；[版本化契约](openapi/control-mileage.json)不手工编辑。
- Spectral CLI `6.15.0` 使用锁文件安装，执行标准规则和[项目规则](openapi/.spectral.yaml)。六个破坏性输入样例验证检查确实能拒绝缺失操作标识、重复标识、无效引用、非法类型、缺少字段说明和正常入口没有成功响应。
- 工具另外检查六份契约所有嵌套 Schema 的显式类型、引用或组合约束，并用丢失类型反例验证；共 8 项工具测试。开放扩展值仍允许任意 JSON。
- HTTP 测试通过 networknt JSON Schema Validator `1.5.6` 按 JSON Schema 2020-12 验证真实响应；校验器只存在于测试依赖。
- `MileageServiceTest`、`MileageOpenApiContractTest`、`MileageOpenApiDisabledTest` 验证实际查询、错误响应和文档端点关闭。
- BFF 复用 Control 真实 HTTP 测试生成的[响应样例](openapi/fixtures)，验证客户端消费、统计/全景的无数据与真实零值，以及浏览器入口认证。

现有的本地时间、缺省设备查询、null/0、数组顺序和无 `quality` 字段行为保持一致。
Control 此方法不执行用户身份解析，契约如实写明受控内网边界；BFF JWT 行为由原安全配置及回归测试保障。

- 文件契约见 [Media 文件](openapi/media-files.json)和 [Control 文件](openapi/control-files.json)。分别描述 multipart 表单、JSON、空响应、文件二进制、HLS 和回调，不改变既有身份、分页、播放 token 或错误行为。
- Media HTTP 测试覆盖 30 条映射、参数绑定、真实序列化、关键错误及文档默认关闭；业务服务使用 mock。Control 的 9 条映射使用真实 `ControlMediaServiceClient` 消费 Media HTTP 生成的文件样例，验证身份头、路径、JSON、播放地址改写、二进制和错误透传。
- 本地入口同时运行文件业务测试，以及 BFF 通用代理、鉴权和既有里程消费回归；这些测试不替代浏览器上传播放或真实存储验收。
- `media-common` 只增加 Swagger `2.2.47` 注解元数据，未增加 Spring 依赖或业务逻辑。Media 生成器与 Control 使用相同版本；请求绑定、字段校验和实际权限仍由原代码执行。

- 新增 [Media 服务](openapi/media-service.json)、[Control 服务](openapi/control-service.json)、[BFF 自有](openapi/bff.json)三份契约；使用 Spring 映射清单断言所有本仓库具体入口都有文档。默认文档与分组同时加载全部 Controller 后，仍与原文件/里程契约一致。
- 视频 HTTP 样例由 Media 生成，再通过真实 Control 客户端消费；测试区分空正文、可空枚举、上海时间与目录 RFC3339、调用方身份、版本头、回调和二进制。BFF 使用真实聚合服务验证响应，外部源和 JWT 解码以替身隔离。
- Control 错误契约区分本地三字段错误和 Media 透传错误；回归覆盖上游 404/503 及必填项、字段类型反例。BFF 全景 400 同时描述业务错误和框架错误，使用真实 HTTP 验证非法 JSON、缺少请求体及错误模型反例，保留现有运行时行为。
- 动态设备参数、状态和 Management 扩展仍按专项协议解释；Schema 保留扩展字段，不代表这些字段已全部完成细化。BFF 当前访问上下文模型已只读核对 Management 源码及 ID 序列化，尚无外部在线验收。
- Spectral 仅对已移除的 `/api/control/robots` 路径关闭“必须存在成功响应”的通用建议，因为其实际返回 410；其余路径仍检查，新增反例验证普通接口缺少成功响应必然失败。

正常检查在模块 `target/openapi/` 输出生成结果，并与 `quality/openapi/` 下六份版本化契约比较；变化会导致失败。
确认是有意的接口变化后才执行：

```bash
mvn -f media-common/pom.xml install -DskipTests
mvn -f control-service/pom.xml -Dtest=MileageOpenApiContractTest -Dopenapi.update=true test
# 文件样例先由 Media 生成，再由 Control 验证消费并生成代理契约。
mvn -f backend/pom.xml -Dtest=FileOpenApiContractTest -Dopenapi.update=true test
mvn -f control-service/pom.xml -Dtest=FileOpenApiContractTest -Dopenapi.update=true test
mvn -f backend/pom.xml -Dtest=ServiceOpenApiContractTest -Dopenapi.update=true test
mvn -f control-service/pom.xml -Dtest=ServiceOpenApiContractTest -Dopenapi.update=true test
mvn -f bigscreen-bff/pom.xml -Dtest=OpenApiContractTest -Dopenapi.update=true test
git diff -- quality/openapi
sh scripts/quality-check.sh openapi
```

生成更新必须审查消费者兼容性；不能仅更新快照使检查变绿。本批尚未接入 oasdiff，不能将快照差异检查称为自动破坏性变更分类。

## 4. 文档端点与后续工作

Media 默认 `MEDIA_OPENAPI_ENABLED=false`，Control 默认 `CONTROL_OPENAPI_ENABLED=false`。仅在受控开发环境显式开启：Media `/v3/api-docs` 提供文件契约，Control `/v3/api-docs` 保留里程查询、`/v3/api-docs/files` 提供文件代理契约；两个服务的 `/v3/api-docs/service` 提供其余接口；BFF 使用 `BFF_OPENAPI_ENABLED=false`，开启后 `/v3/api-docs` 提供自有契约。均未引入 Swagger UI。
部署配置 `server.error.include-*` 会影响框架错误响应的附加字段，本契约保留扩展字段能力。

本轮使用本地命令、自查和代码审查，按存量清单逐业务域整改并验证接口兼容性。CI 平台接入及远端必过检查是后续可选项，不纳入本轮实施或验收。
根目录 `scripts/dev-check.sh` 仍是构建入口，不替代本目录的规则和契约检查。

## 5. 整改记录的唯一来源

[整改台账](../docs/02-设计/工程规范/存量规范整改台账-20260929.md)维护批次状态；[执行记录](../docs/04-测试与验收/执行记录/规范整改执行记录-20260929.md)保留实际验证和历史工具取舍。逐项问题只维护 `audit/20260929/java-findings.json`，HTTP 映射只维护 `audit/20260929/http-mappings.json`，不再复制为 Markdown 明细。

两份 JSON 保留原始问题证据、行号和源码哈希，后续只更新处置与契约状态；它们不是运行依赖或规则豁免，检查脚本不会读取它们来放行告警。逐文件哈希清单、完整扫描报告、临时输入和日志位于被忽略的 `target/quality/`。不要将本地报告重复纳入版本管理。
