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

- `BigscreenProxyController`：代理 `/api/control/**`、`/api/media/**`、`/internal/media/**`、`/api/manage/**` 和 `/api/v1/management/**`；`GET /api/control/robots` 固定返回 `410`。
- `BusinessTaskProxyController`：只代理任务计划、流程定义、执行记录、设备和地图白名单。
- `PanoramaService`：并行查询管理端与 Control，组装 overview、设备详情、任务和告警。
- `StatisticsService`：返回统计结构，并同步生成/保存 PDF；当前业务统计值未接入权威统计源。
- `BigscreenWebSocketBridgeHandler`：为每个浏览器连接建立一条 Control 上游连接。
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
| `/api/bigscreen/statistics/**` | 统计结构、同步 PDF 导出和本地报告历史 |
| `/api/bigscreen/business/**` | 有限白名单的 Management 业务代理 |
| `/api/control/**`、`/api/media/**` 等 | 下游透明代理 |
| `/ws/control`、`/ws/media`、`/ws/bigscreen` | 同一 WebSocket 桥接处理器的兼容路径 |

固定摄像头会作为全景 `devices[]` 中的同级装备返回。当前 `status=online` 只表示管理端 `enabled=true`，不是 Gateway 探活；`playable=true` 还要求存在主或子码流。BFF 不返回 RTSP URL。

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
| `BIGSCREEN_AUTH_CLIENT_ID` | JWT `azp/aud` 目标客户端 |
| `BIGSCREEN_AUTH_ISSUER_URI`、`BIGSCREEN_AUTH_JWK_SET_URI` | JWT Issuer 与 JWK |
| `BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS 来源模式 |
| `STATISTICS_REPORT_STORAGE_DIR` | PDF 与报告索引目录 |

生产必须使用真实 HTTPS Issuer/JWK、受控 CORS 和内部下游地址。若 REST 正常但 Nginx 返回 502，应检查 upstream，并确认代理未重复转发 `Connection`、`Transfer-Encoding`、`Content-Length`、`Upgrade` 等 hop-by-hop Header。

新增聚合字段时必须同步维护字段来源映射；下游不可用时应保留明确的空值或错误语义，不新增生产假数据兜底。
