# Bigscreen BFF

`bigscreen-bff/` 是面向指挥中心和大屏前端的 REST/WebSocket 代理与聚合服务。它负责组合管理、控制和媒体服务的数据，不承载媒体流，也不复制下游服务的核心业务逻辑。

## 代码结构

```text
src/main/java/com/robot/bigscreen/
├── api/          通用代理和任务代理接口
├── client/       下游中心服务 HTTP 客户端
├── config/       服务地址、跨域和 WebSocket 配置
├── panorama/     全景地图聚合接口与字段转换
├── statistics/   大屏统计和报告
└── ws/           WebSocket 桥接与事件适配
```

应用入口为 `BigscreenBffApplication`，默认端口为 `8090`。

## 环境要求

- JDK 17
- Maven 3.9+
- 可访问的管理服务、Control Service 和 Media Service

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BIGSCREEN_BFF_PORT` | `8090` | HTTP 服务端口 |
| `CENTER_MANAGE_BASE_URL` | `http://host.docker.internal:8866` | 管理服务地址 |
| `CENTER_CONTROL_BASE_URL` | `http://control-service:8082` | Control Service 地址 |
| `CENTER_V1_CONTROL_BASE_URL` | `http://host.docker.internal:8867` | 旧版控制服务地址 |
| `CENTER_MEDIA_BASE_URL` | `http://media-service:8088` | Media Service 地址 |
| `CENTER_CONTROL_WS_URL` | `ws://control-service:8082/ws/control` | Control WebSocket 地址 |
| `BIGSCREEN_AUTH_CLIENT_ID` | `bigscreen-web` | BFF 接受的 Keycloak 客户端 |
| `BIGSCREEN_AUTH_ISSUER_URI` | `http://localhost:18443/realms/iam-auth` | Keycloak Token 签发者 |
| `BIGSCREEN_AUTH_JWK_SET_URI` | `http://localhost:18443/realms/iam-auth/protocol/openid-connect/certs` | Keycloak JWK 公钥地址 |
| `STATISTICS_REPORT_STORAGE_DIR` | `data/statistics-reports` | 统计报告存储目录 |
| `BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS` | 见 `application.yml` | 允许跨域访问的来源 |

## 启动、构建与测试

```bash
cd bigscreen-bff
mvn spring-boot:run
mvn test
mvn -q -DskipTests package
```

## 主要接口

- `/api/bigscreen/panorama/**`：全景地图、设备、任务和告警聚合。
- `/api/bigscreen/statistics/**`：统计总览和报告。
- `/api/bigscreen/proxy/**`：受控的下游接口代理。
- `/ws/bigscreen`：大屏事件连接。

## 联调检查

本地接口：

```bash
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://127.0.0.1:8090/api/bigscreen/panorama/overview
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://127.0.0.1:8090/api/bigscreen/panorama/devices/test111
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://127.0.0.1:8090/api/bigscreen/panorama/tasks
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://127.0.0.1:8090/api/bigscreen/panorama/alarms
curl -sS -X POST http://127.0.0.1:8090/api/bigscreen/panorama/alarms/alarm-001/disposal \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"disposalStatus":"IMMEDIATE_DISPOSAL"}'
```

如果直连 `8090` 正常而 Nginx 入口失败，优先检查 Nginx upstream 和错误日志。代理响应需要过滤 `Transfer-Encoding`、`Connection`、`Content-Length`、`Upgrade` 等 hop-by-hop Header，避免重复 `Transfer-Encoding: chunked` 导致 502。

接口字段和业务设计以以下文档为准：

- [大屏 BFF 全景地图设计说明书](../docs/02-设计/大屏BFF/大屏BFF全景地图设计说明书.md)
- [大屏统一登录认证对接指南](../docs/03-接口与协议/大屏BFF/大屏统一登录认证对接指南.md)
- [大屏 BFF 字段来源映射文档](../docs/03-接口与协议/大屏BFF/大屏BFF字段来源映射文档.md)
- [大屏统计接口文档](../docs/03-接口与协议/大屏BFF/大屏统计接口文档.md)
- [大屏 BFF 测试方案](../docs/04-测试与验收/测试方案/大屏BFF测试方案.md)

## 开发约定

- BFF 只做接口代理、字段转换和面向页面的聚合。
- 下游没有真实数据时返回 `null`、空集合或明确的不可用状态，不生成生产假数据。
- 新增聚合字段时同步维护字段来源映射文档。
- 修改跨服务调用时覆盖成功、超时、下游错误和字段缺失场景。
