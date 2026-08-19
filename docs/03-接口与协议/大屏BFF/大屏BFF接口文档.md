# Bigscreen BFF 接口文档

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 当前代码基线 |
| 基线日期 | 2026-08-12 |
| 服务端口 | `8090` |

## 1. 边界与鉴权

Bigscreen BFF 是大屏前端统一 REST/WebSocket 入口，负责 JWT 验证、请求代理、字段聚合和事件适配。它不承载 WebRTC 媒体流，也不在下游失败时生成业务快照假数据。鉴权、用户 Header、时间和错误约定见[Java 服务接口通用约定](../公共约定/Java服务接口通用约定.md)。

## 2. 全景聚合接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bigscreen/panorama/overview` | 聚合设备统计、机器人/固定摄像头、地图、任务、告警摘要 |
| `GET` | `/api/bigscreen/panorama/devices/{deviceId}` | 查询单个设备聚合详情 |
| `GET` | `/api/bigscreen/panorama/tasks` | 查询当前任务快照 |
| `GET` | `/api/bigscreen/panorama/alarms` | 查询告警分组快照 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/disposal` | 处置告警 |
| `GET` | `/api/bigscreen/panorama/alarms/actionable-workflow` | 查询当前用户可处理的工作流告警 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/handle-and-continue` | 处置告警并继续对应工作流 |

响应字段、来源优先级和空值规则以[大屏 BFF 字段来源映射文档](大屏BFF字段来源映射文档.md)为准。固定摄像头作为 `devices[]` 中的同级装备返回，关键识别字段为：

```json
{
  "robotId": "camera-001",
  "cameraId": "camera-001",
  "typeCode": "FIXED_CAMERA",
  "equipmentType": "FIXED_CAMERA",
  "sourceType": "FIXED_CAMERA",
  "status": "online",
  "defaultQuality": "sub",
  "playable": true,
  "showControlCenter": false,
  "showController": false
}
```

这里的 `status=online` 表示管理端配置 `enabled=true`，不是 Gateway 探活结果；`playable=true` 还要求至少配置主码流或子码流。BFF 不向前端返回 RTSP URL 或摄像头凭据。

## 3. 统计与报告接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bigscreen/statistics/overview` | 返回筛选范围、设备类型选项及统计结构 |
| `POST` | `/api/bigscreen/statistics/reports/export` | 同步生成并下载 PDF，同时落本地历史记录 |
| `GET` | `/api/bigscreen/statistics/reports` | 查询本地报告历史，`page` 从 1 开始 |
| `GET` | `/api/bigscreen/statistics/reports/{id}/download` | 下载历史 PDF |
| `DELETE` | `/api/bigscreen/statistics/reports/{id}` | 删除历史 PDF 和索引记录，成功返回 `204` |

统计业务值目前未接入权威统计源，除筛选信息和设备类型选项外返回 `null` 或空集合；报告是同步响应，不存在“创建异步任务后轮询状态”的 Java 接口。详细字段见[大屏统计接口文档](大屏统计接口文档.md)。

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
| `/api/media/**`、`/internal/media/**` | Media Service | 原路径 |
| `/api/manage/**`、`/api/v1/management/**` | Management Service | 原路径 |

代理支持普通正文和 `multipart/form-data`。请求会过滤 hop-by-hop Header，并用 JWT 身份覆盖可信用户 Header。

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

每个浏览器连接对应一条到 `center.websocket-control-url` 的上游连接。浏览器消息原样转发给上游；上游原始消息也原样回传，同时可能派生 `panorama.*` 事件。

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
