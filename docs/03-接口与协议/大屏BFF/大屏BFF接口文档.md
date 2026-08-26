# Bigscreen BFF 接口文档

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 当前代码基线 |
| 基线日期 | 2026-08-25 |
| 服务端口 | `8090` |

## 1. 边界与鉴权

Bigscreen BFF 是大屏前端统一 REST/WebSocket 入口，负责 JWT 验证、请求代理、字段聚合和事件适配。它不承载 WebRTC 媒体流，也不在下游失败时生成业务快照假数据。鉴权、用户 Header、时间和错误约定见[Java 服务接口通用约定](../公共约定/Java服务接口通用约定.md)。

## 2. 全景聚合接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bigscreen/panorama/overview` | 首屏摘要：设备、统计、任务摘要、地图摘要和告警；不加载地图点、任务路径、回放或逐设备详情 |
| `GET` | `/api/bigscreen/panorama/maps/{mapId}/scene` | 当前地图的点位与固定摄像头；首屏默认地图和用户切图时调用 |
| `GET` | `/api/bigscreen/panorama/maps/{mapId}/task-routes` | 当前地图关联任务的路径点；不加载任务回放或设备任务详情 |
| `GET` | `/api/bigscreen/panorama/devices/{deviceId}` | 查询单个设备聚合详情 |
| `GET` | `/api/bigscreen/panorama/tasks` | 查询当前任务快照 |
| `GET` | `/api/bigscreen/panorama/tasks/{taskId}` | 用户打开任务时查询单个任务完整详情（含回放和设备任务明细） |
| `GET` | `/api/bigscreen/panorama/alarms` | 查询告警分组快照 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/disposal` | 处置告警 |
| `GET` | `/api/bigscreen/panorama/alarms/actionable-workflow` | 查询当前用户可处理的工作流告警 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/handle-and-continue` | 处置告警并继续对应工作流 |
| `GET` | `/api/bigscreen/access-control/me` | 代理当前登录用户的管理端数据权限；下游失败时拒绝访问，不返回默认权限 |

响应字段、来源优先级和空值规则以[大屏 BFF 字段来源映射文档](大屏BFF字段来源映射文档.md)为准。固定摄像头作为 `devices[]` 中的同级装备返回，关键识别字段为：

```json
{
  "robotId": "camera-001",
  "cameraId": "camera-001",
  "typeCode": "FIXED_CAMERA",
  "equipmentType": "FIXED_CAMERA",
  "sourceType": "FIXED_CAMERA",
  "status": "online",
  "enabled": true,
  "configStatus": "READY",
  "configReady": true,
  "gatewayId": "default",
  "gatewayHealth": {"status": "ONLINE", "observedAt": "2026-08-23T10:00:00Z", "reasonCode": null},
  "streamHealth": {"status": "AVAILABLE", "observedAt": "2026-08-23T10:00:00Z", "reasonCode": null},
  "defaultQuality": "sub",
  "playable": true,
  "showControlCenter": false,
  "showController": false
}
```

设备对外 `status` 仅为 `online/fault/offline`。固定摄像头 `status=online` 仅在配置启用且完整、Gateway
心跳有效并且最近 RTSP 探测成功时成立；配置停用、配置无效、健康接口不可用、消息缺失或过期均为
`offline`，不得回退 `enabled=true`。`playable` 是兼容字段，只表示 `enabled && configReady`，不表示
在线，新调用方应使用 `configReady`。BFF 不向前端返回 RTSP URL 或摄像头凭据。

`overview` 和任务快照均携带稳定的任务数据质量字段。任务查询失败时 HTTP 仍可返回已成功
加载的部分数据，但调用方必须按 `degraded=true` 处理，不能把 `items=[]` 解释为真实的 0：

```json
{
  "dataQuality": {
    "tasks": {
      "complete": false,
      "degraded": true,
      "reasonCodes": ["TASK_QUERY_TIMEOUT"],
      "invalidReferenceCount": 1,
      "invalidWorkflowReferences": ["workflow-instance-404"]
    }
  }
}
```

常见 reason code 包括 `TASK_QUERY_TIMEOUT`、`TASK_QUERY_CONCURRENCY_LIMIT`、
`TASK_EXECUTOR_SATURATED`、`TASK_PAGINATION_LIMIT`、`TASK_INVALID_RESPONSE`、
`WORKFLOW_INSTANCE_NOT_FOUND` 和 `WORKFLOW_DEFINITION_NOT_FOUND`。401/403 不进入降级响应。

### 2.1 按需读取时序与响应边界

页面首屏先请求 `overview`；若默认地图为 SLAM，再并行请求该地图的 `scene` 和 `task-routes`。用户切换
SLAM 地图时，仅请求目标 `mapId` 的这两个资源；用户打开任务视频时，才请求 `tasks/{taskId}`。前端不应在
首屏、定时刷新或切图时预取任务回放、设备任务明细或非当前地图点位。

`scene` 响应为 `{serverTime, mapId, points, fixedCamares}`，其中 `points` 为当前地图点位，
`fixedCamares` 保持现有字段拼写，表示当前地图固定摄像头。`task-routes` 响应为
`{serverTime, mapId, items, dataQuality}`；每个 `items[]` 包含 `taskId`、`workflowInstanceId`、`mapId` 和
`pathPoints`。`tasks/{taskId}` 响应为 `{serverTime, task, dataQuality}`；找不到任务时 `task=null`，不以
伪造任务替代。

为抑制同一用户高频刷新，BFF 对同一 JWT `sub` 合并在途 `overview` 并仅复用 5 秒内的成功结果；失败结果
不缓存。地图场景、地图任务路径和可处置工作流告警按同一用户合并并成功缓存 3 秒。Management 通用资源
请求使用独立连接/读取时限（默认各 1000/1500 ms）及单实例公平并发上限（默认 16）；任务请求继续使用
独立的默认 1000/1500 ms、8 并发边界。缓存不跨用户共享，也不改变 401/403 语义。

## 3. 统计与报告接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bigscreen/statistics/overview` | 基于当前用户授权设备、任务、告警和 Control 里程汇总统计 |
| `POST` | `/api/bigscreen/statistics/reports/export` | 同步生成并下载 PDF，同时落本地历史记录 |
| `GET` | `/api/bigscreen/statistics/reports` | 查询当前用户报告历史，`page` 从 1 开始；当前生产数据来自单实例持久化目录 |
| `GET` | `/api/bigscreen/statistics/reports/{id}/download` | 下载历史 PDF |
| `DELETE` | `/api/bigscreen/statistics/reports/{id}` | 删除历史 PDF 和索引记录，成功返回 `204` |

统计接口当前已接入管理端设备/任务/告警及 Control 里程汇总；确实没有数据时字段仍返回 `null` 或空集合，无有效里程样本时页面展示 `--`。报告是同步响应，不存在“创建异步任务后轮询状态”的 Java 接口。详细字段见[大屏统计接口文档](大屏统计接口文档.md)。

## 4. 业务管理代理

`/api/bigscreen/business/**` 只接受以下白名单映射，方法、查询参数、请求体和下游响应保持不变：

| BFF 路径 | Management Service 路径 |
| --- | --- |
| `/api/bigscreen/business/tasks/plans/**` | `/api/v1/management/task-workflow-plans/**` |
| `/api/bigscreen/business/tasks/workflow-definitions/**` | `/api/v1/management/task-workflow-definitions/**` |
| `/api/bigscreen/business/tasks/execution-records/**` | `/api/v1/management/task-workflow-instances/**` |
| `/api/bigscreen/business/devices/**` | `/api/v1/management/devices/**` |
| `/api/bigscreen/business/maps/**` | `/api/v1/management/maps/**` |

不在白名单内的路径返回 `404`。当前 BFF 没有 `/quick-tasks` 或 `/task-executions` 专用接口；此前的快捷任务契约已移入归档，不能用于当前联调。任务计划、流程定义和执行记录只能通过上述业务白名单代理访问。

## 5. 通用透明代理

| 入站前缀 | 目标服务 | 路径变换 |
| --- | --- | --- |
| `/api/control/**` | Control Service | 原路径 |
| `/api/bigscreen/control/**` | Control Service | 改写为 `/api/control/**` |
| 其他未被本地 Controller 消费的 `/api/bigscreen/**` | Control Service | 改写为 `/api/control/**` |
| `/api/media/**` | Media Service | 原路径 |
| `/api/manage/**`、`/api/v1/management/**` | Management Service | 原路径 |

代理支持普通正文和 `multipart/form-data`。请求会过滤 hop-by-hop Header，并用 JWT 身份覆盖可信用户 Header。`/internal/**` 不属于 BFF 对外代理范围，生产 Nginx 对 `/internal`、`/internal/**`、`/api-gw/internal` 和 `/api-gw/internal/**` 明确返回 `404`；Control 仍通过内网地址直接调用 Media 内部接口。

`GET /api/control/robots` 已显式移除，固定返回 `410 Gone`：

```json
{
  "code": "API_REMOVED",
  "message": "Use /api/bigscreen/panorama/overview instead of /api/control/robots."
}
```

## 6. WebSocket 桥接

BFF 在三个兼容路径注册同一桥接处理器：

```text
/ws/control
/ws/media
/ws/bigscreen
```

每个浏览器连接对应一条到 `center.websocket-control-url` 的上游连接。BFF 在转发浏览器消息前检查 Token 和当前授权快照；显式携带无权 `robotId` 或 `cameraId` 的消息不会转发到 Control。通过预校验的消息原样转发；上游原始消息按授权资源过滤后回传，同时可能派生 `panorama.*` 事件。

授权快照最大陈旧时间为 30 秒。初始加载或刷新失败使用 `4003` 关闭连接；握手 JWT 到达 `exp` 后使用 `4001` 关闭。资源集合发生变化时连接保持有效，BFF 发送以下通知，调用方必须重新请求 `/api/bigscreen/panorama/overview`，并以响应完整替换旧设备集合：

```json
{
  "event": "bigscreen.authorization.changed",
  "timestamp": "2026-08-23T22:00:00Z",
  "data": {
    "reason": "AUTHORIZED_RESOURCES_CHANGED"
  }
}
```

该通知不携带授权 ID 明细，Overview 是页面完整快照的权威来源。前端应先清除旧资源再请求 Overview；请求失败时不得恢复已清除的旧资源。WebSocket 断线重连成功后也应重新请求 Overview，以覆盖断线期间的权限变化。

显式资源预校验失败时，BFF 返回带原 `requestId` 的失败回执；普通控制消息使用 `control.command.rejected`，对讲消息使用 `video.intercom.call.operation-failed`：

```json
{
  "type": "control.command.rejected",
  "requestId": "request-001",
  "timestamp": "2026-08-23T22:00:00Z",
  "payload": {
    "code": "RESOURCE_FORBIDDEN",
    "message": "当前用户无权操作目标资源"
  }
}
```

事件适配规则：

| 上游事件 | 派生事件                                                                                                                                                                                                   |
| --- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `robot.state` | `panorama.device.status.changed`：仅边缘状态（`stateSource=EDGE_DEVICE_STATUS`）与离线扫描（`stateSource=OFFLINE_SCAN`）来源派生，媒体客户端来源（`stateSource=MEDIA_CLIENT_STATUS`）不派生；有定位时再派生 `panorama.device.location.changed` |
| `panorama.device.location.changed` | 按浏览器会话和 `robotId` 隔离；首条立即推送，1 秒内只保留最新一条，每秒最多一次；无新定位不重复推送旧坐标                                                                                                                                            |
| task 变更类事件 | 有完整 `taskId` 时立即转换为 `panorama.task.changed`                                                                                                                                                            |
| `management.task.invalidated` | 300ms 去抖后重查任务权威快照，只推送变化项                                                                                                                                                                               |
| alarm 变更类事件 | 立即转换为 `panorama.alarm.changed`，无真实上游事件时不生成模拟告警                                                                                                                                                         |
| `management.alarm.invalidated` | 300ms 去抖后重查告警权威快照，只推送变化项                                                                                                                                                                               |
| 设备、任务、告警或机器人在线状态变化 | 500ms 去抖后按事件类型只重算受影响统计块（设备/任务/告警，各块 3 秒 TTL 缓存按用户隔离），仅在快照变化时推送 `panorama.stats.changed`                                                                                                                |

当前代码仍对没有定位的 `test111`、`SN005`、`SN006` 生成硬编码演示位置事件。它不是管理端真实位置，也不是通用兜底；生产验收不得把这些事件作为真实定位依据。其他机器人无定位时不补位置事件。若上游 WebSocket 不可用，连接仍可建立，但不会收到上游动态事件，也不会凭空生成业务快照。
