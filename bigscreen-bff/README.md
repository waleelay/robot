# Bigscreen BFF

`bigscreen-bff/` 是 Java 17 + Spring Boot 3 大屏统一接入层，默认端口 `8090`。它验证 JWT、代理 Management/Control/Media、聚合大屏数据并桥接 WebSocket；不承载 WebRTC 媒体流，也不复制下游核心业务。

接口权威定义见 [大屏 BFF 接口文档](../docs/03-接口与协议/大屏BFF/大屏BFF接口文档.md)。

## 1. 启动、构建与测试

```bash
cd bigscreen-bff
mvn spring-boot:run
mvn test
mvn -q -DskipTests package
```

启动时需要配置可用的 JWT Issuer/JWK。聚合与代理能力还需要 Management、Control 和 Media；WebSocket 动态事件依赖 Control `/ws/control`。

## 2. 代码结构

```text
src/main/java/com/robot/bigscreen/
├── api/         通用透明代理和业务白名单代理
├── auth/        JWT claim 到可信下游 Header 的映射
├── client/      下游路径选择、请求转发和 Header 过滤
├── config/      JWT、CORS、服务地址和 WebSocket 注册
├── panorama/    设备、任务、告警和地图聚合
├── statistics/  统计结构、本地 PDF 报告和历史索引
└── ws/          上游桥接、事件转换、任务/统计去抖刷新
```

- `BigscreenProxyController`：代理 `/api/control/**`、`/api/media/**`、`/api/manage/**` 和 `/api/v1/management/**`；`GET /api/control/robots` 固定返回 `410`。`/internal/**` 仅供服务间内网调用，不注册为 BFF 对外代理。
- `BusinessTaskProxyController`：只代理任务计划、流程定义、执行记录、设备和地图白名单。
- `PanoramaService`：并行查询管理端与 Control，组装 overview、设备详情、任务和告警。
- `StatisticsService`：基于授权设备、实时状态、任务、告警和 Control 里程汇总统计，并同步生成/保存 PDF；缺少权威来源的指标保持 `null`，任务查询失败时返回 `dataQuality.tasks`，不把空结果伪装成真实的 0。
- `BigscreenWebSocketBridgeHandler`：为每个浏览器连接建立一条 Control 上游连接；按用户和组织复用最长 30 秒的授权快照，在事件下发和控制上行前强制检查快照及 Token 有效期。后台刷新暂时失败时保留尚未过期的授权快照并继续重试；JWT 到期或 Management 明确返回 `401` 时以 `4001` 关闭，避免节点时钟漂移把凭证失效误报为权限服务故障；只有其他授权刷新失败持续至快照超过最大陈旧时间才以 `4003` 关闭。资源集合确认变化时发送 `bigscreen.authorization.changed`，通知前端重拉 Overview。
- `PanoramaWebSocketEventAdapter`：将 `robot.state` 等事件适配成 `panorama.*`。
- `PanoramaTaskEventRefresher` / `PanoramaStatsEventRefresher`：分别以 300ms/500ms 去抖查询权威快照并按差异推送。

## 3. 鉴权与信任边界

BFF 是 OAuth2 Resource Server：

- `/api/**`、`/ws/**` 默认需要 JWT；`OPTIONS`、`/error` 和带播放 Token 的 HLS GET 例外。
- 只接受 `ES256`/`RS256`，并校验 issuer 及 `azp` 或 audience 中的 BFF client ID。
- REST 从 Bearer Header 读取 Token；WebSocket 还允许 `access_token` 查询参数。
- 浏览器传入的 `X-User-Id`、`X-Org-Id`、`X-Roles` 会先被删除，再从已验签 JWT 重建。
- 管理员角色会补充 `MEDIA_VIEWER`、`MEDIA_OPERATOR`、`EQUIPMENT_OPERATOR`。

完整 claim/Header 映射见 [Java 服务接口通用约定](../docs/03-接口与协议/公共约定/Java服务接口通用约定.md)。

## 4. 接口与数据语义

| 路径 | 当前语义 |
| --- | --- |
| `/api/bigscreen/panorama/**` | 全景总览、设备详情、任务、告警及处置 |
| `/api/bigscreen/statistics/**` | 统计结构、同步 PDF 导出和按用户隔离的报告历史 |
| `/api/bigscreen/business/**` | 有限白名单的 Management 业务代理 |
| `/api/control/**`、`/api/media/**` 等 | 下游透明代理 |
| `/ws/control`、`/ws/media`、`/ws/bigscreen` | 同一 WebSocket 桥接处理器的兼容路径 |

固定摄像头会作为全景 `devices[]` 中的同级装备返回。BFF 合并 Control 健康快照，分别输出
`enabled/configReady`、`gatewayHealth` 和 `streamHealth`；只有配置启用且完整、Gateway 在线、
RTSP 可用时 `status=online`，缺失或过期状态为 `unknown`。`playable` 仅为配置门槛兼容字段，
不表示在线。BFF 不向浏览器返回 RTSP URL。WebSocket 授权快照完整加载后，BFF 会把当前身份
可见的固定摄像头配置以 180 秒短租约发送给 Control；快照每 30 秒刷新一次，查询或同步失败时
不续租，旧目录到期后 Gateway 停止周期探测。这是有用户会话期间的按需健康链路，不承诺
7×24 小时全量监测。

下游缺失字段通常返回 `null` 或空集合。代码仍为 `test111`、`SN005`、`SN006` 保留无定位时的硬编码演示位置事件；该兼容只影响 WebSocket 事件，不能作为生产真实定位。

## 5. 配置

| 环境变量 | 说明 |
| --- | --- |
| `BIGSCREEN_BFF_PORT` | HTTP 端口 |
| `CENTER_MANAGE_BASE_URL` | Management 地址 |
| `CENTER_CONTROL_BASE_URL` | Control 地址 |
| `CENTER_V1_CONTROL_BASE_URL` | 旧版控制服务地址，供全景聚合内部查询设备实时状态；BFF 未对外注册 `/api/v1/control/**` 透明代理 |
| `CENTER_MEDIA_BASE_URL` | Media 地址 |
| `CENTER_CONTROL_WS_URL` | Control WebSocket 地址 |
| `BIGSCREEN_WS_AUTHORIZATION_MAX_STALENESS_MS` | WebSocket 授权快照最大陈旧时间，默认 30000，生产不得调大 |
| `BIGSCREEN_WS_AUTHORIZATION_CHECK_INTERVAL_MS` | Token 和授权快照检查周期，默认 1000 |
| `BIGSCREEN_WS_AUTHORIZATION_LOAD_TIMEOUT_MS` | 单次完整授权加载总时限，默认 8000 |
| `FIXED_CAMERA_CATALOG_LEASE_ENABLED` | 是否向 Control 同步固定摄像头短租约，默认 `true` |
| `FIXED_CAMERA_CATALOG_LEASE_SECONDS` | 租约时长，默认 180 秒，代码硬上限 300 秒 |
| `PANORAMA_TASK_CONNECT_TIMEOUT_MS` | Management 任务专用连接超时，默认 1000 ms，代码限制 100 至 5000 ms |
| `PANORAMA_TASK_READ_TIMEOUT_MS` | Management 任务专用读取超时，默认 1500 ms，代码限制 100 至 10000 ms |
| `PANORAMA_TASK_MAX_CONCURRENCY` | 单实例 Management 任务请求并发上限，默认 8，代码限制 1 至 32 |
| `BIGSCREEN_AUTH_CLIENT_ID` | JWT `azp/aud` 目标客户端 |
| `BIGSCREEN_AUTH_ISSUER_URI`、`BIGSCREEN_AUTH_JWK_SET_URI` | JWT Issuer 与 JWK |
| `BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 来源模式 |
| `STATISTICS_REPORT_STORAGE_DIR` | 单实例 PDF 与原子索引的持久化目录 |
| `STATISTICS_REPORT_MINIMUM_FREE_BYTES` | 生成新报告后必须保留的磁盘余量，默认 1 GiB |
| `STATISTICS_REPORT_INSTANCE_COUNT` | 必须为 `1`；其他值会拒绝启动，扩容前需另行实施共享存储 |

生产必须使用真实 HTTPS Issuer/JWK、受控 CORS 和内部下游地址。若 REST 正常但 Nginx 返回 502，应检查 upstream，并确认代理未重复转发 `Connection`、`Transfer-Encoding`、`Content-Length`、`Upgrade` 等 hop-by-hop Header。

新增聚合字段时必须同步维护字段来源映射；下游不可用时应保留明确的空值或错误语义，不新增生产假数据兜底。

全景今日里程和统计页区间里程由 Control 对边缘状态上报持久化计算，BFF 通过
`/api/control/statistics/mileage` 批量查询，展示单位统一换算为 `KM`。缺少有效样本时保持
`null` 并展示 `--`；`dataQuality.mileage` 只透传总里程是否有数据、简化质量状态和时区。
`quality=UNKNOWN` 时总里程仍可展示，但页面与 PDF 必须提示历史质量无法精确分类。

生产报告通过 `StatisticsReportStore` 的本地实现保存到专用持久化目录，`index.json` 采用临时文件
加原子替换。当前边界只允许单实例；写入前检查磁盘余量，部署需监控容量、目录可写和清理异常，
并对整个报告目录执行备份恢复演练。
