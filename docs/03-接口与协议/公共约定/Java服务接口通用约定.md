# Java 服务接口通用约定

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 当前代码基线 |
| 基线日期 | 2026-08-23 |
| 适用模块 | `bigscreen-bff`、`control-service`、`backend` |

## 1. 生产入口与信任边界

生产请求应先到 Bigscreen BFF。BFF 是 OAuth2 Resource Server，验证 Bearer JWT 后才将请求代理给下游。Control Service 和 Media Service 当前不独立验签 JWT，而是信任受控上游注入的用户上下文 Header，因此不能把它们无防护地暴露到不可信网络。

开发环境可直连 Control 或 Media，但它们在 Header 缺失时会使用开发默认身份。该行为只用于本地联调，不是匿名生产授权机制。

## 2. JWT 验证规则

Bigscreen BFF 对 `/api/**` 和 `/ws/**` 要求认证，但以下请求例外：

- `OPTIONS /**`；
- `/error`；
- `GET /api/control/files/{fileId}/hls/**`，该路径使用播放 URL 中的 `token` 查询参数鉴权。

JWT 必须同时满足：

1. 签名算法为 `ES256` 或 `RS256`；
2. `iss` 等于 `spring.security.oauth2.resourceserver.jwt.issuer-uri`；
3. `azp` 等于 `bigscreen.auth.client-id`，或 `aud` 包含该 client ID。

REST 仅从 `Authorization: Bearer ...` 读取令牌。WebSocket 握手额外允许 `access_token` 查询参数，以适配浏览器无法自定义握手 Header 的场景。

## 3. 下游用户上下文 Header

BFF 会先删除浏览器传入的 `X-User-Id`、`X-Org-Id`、`X-Roles`，再根据已经验签的 JWT 重新生成，防止调用方伪造身份。

| Header | 来源 | 语义 |
| --- | --- | --- |
| `Authorization` | 原 JWT | 继续透传给需要独立鉴权的下游服务 |
| `X-User-Id` | JWT `sub` | 当前用户 ID |
| `X-Org-Id` | 依次取 `org_id`、`orgId`、`organization_id`、`tenant_id` | 当前组织/租户 ID；JWT 无这些 claim 时不注入 |
| `X-Roles` | `roles`、`realm_access.roles`、当前客户端的 `resource_access.*.roles` 合并 | 逗号分隔角色；无角色时为 `AUTHENTICATED` |
| `X-Client-Id` | 浏览器请求头或 WebSocket `clientId` 查询参数 | 区分同一用户的不同终端/标签页；BFF 当前不从 JWT重写此字段 |

若角色集合包含 `platform_admin`、`super_admin` 或 `admin`，BFF 会补充 `MEDIA_VIEWER`、`MEDIA_OPERATOR`、`EQUIPMENT_OPERATOR` 三项业务角色。

Control Service 向 Management Service 发起由 HTTP 请求触发的调用时继续透传 Bearer Token；后台 MQTT 处理没有用户请求上下文时只使用已有缓存或内部数据，不伪造 Bearer Token。

## 4. 开发默认身份

直接访问下游且未提供 Header 时，当前代码使用以下默认值：

| 服务 | `userId` | `orgId` | `clientId` | 默认角色 |
| --- | --- | --- | --- | --- |
| Media Service | `dev-user` | `media.file.default-org-id` | `web` | `MEDIA_VIEWER,MEDIA_OPERATOR` |
| Control Service | `dev-user` | `control.auth.default-org-id` | `web` | `MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR` |

WebSocket 直连 Control 时，`clientId` 优先取握手 Header，其次取 `clientId` 查询参数，最后取 WebSocket session ID。

## 5. 时间格式

Media Service 和 Control Service 的 JSON、MVC 参数及其 WebSocket 事件统一使用上海时区展示格式：

```text
yyyy-MM-dd HH:mm:ss
```

例如：`2026-08-12 21:30:00`。响应字符串不携带 `Z` 或 `+08:00`，语义固定为 `Asia/Shanghai`。

入参为 `OffsetDateTime` 或 `Instant` 时兼容标准 ISO-8601，也兼容上述本地展示格式；BFF 自身组装的全景、统计和事件时间同样输出 `yyyy-MM-dd HH:mm:ss`。下游透明代理的响应格式由目标服务决定。

## 6. 错误响应

Media Service 的业务错误结构：

```json
{
  "timestamp": "2026-08-12 21:30:00",
  "status": 409,
  "code": "INVALID_STATE",
  "message": "当前状态不允许执行该操作",
  "retryable": false,
  "requestId": "req_xxx",
  "path": "/internal/media/video-sessions/vs_xxx/stop",
  "details": {}
}
```

`details` 为空时省略。Media 会接受合法的 `X-Request-Id`，否则生成新 ID，并通过响应 Header 回传；可重试限流或存储故障还会返回 `Retry-After`。

Control Service 对 Media 的非 2xx 响应保留原 HTTP 状态和 JSON 正文。Control 自身参数错误返回 `400 INVALID_CONTROL_REQUEST`；对讲占用返回 `409`，具体 code 为 `ROBOT_BUSY`、`OPERATOR_BUSY` 或 `CLIENT_BUSY`。

BFF 透明代理保留下游状态和正文，并过滤 `Connection`、`Transfer-Encoding`、`Content-Length`、`Upgrade` 等 hop-by-hop Header。

## 7. 分页约定

Media/Control 文件列表使用从 `0` 开始的 `page`。BFF 统计报告历史使用从 `1` 开始的 `page`。Management Service 的代理接口通常使用 `pageNum`，BFF 不改写透明代理的分页语义。
