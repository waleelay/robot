# Bigscreen BFF 接口文档

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 当前代码基线 |
| 基线日期 | 2026-08-29 |
| 服务端口 | `8090` |

## 1. 边界与鉴权

Bigscreen BFF 是大屏前端统一 REST/WebSocket 入口，负责 JWT 验证、请求代理、字段聚合和事件适配。它不承载 WebRTC 媒体流，也不在下游失败时生成业务快照假数据。鉴权、用户 Header、时间和错误约定见[Java 服务接口通用约定](../公共约定/Java服务接口通用约定.md)。

## 2. 全景聚合接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/bigscreen/panorama/overview` | 首屏摘要：设备、统计、任务摘要、地图摘要和告警；`devices[]` 不重复任务，`tasks[]` 不返回路径；不加载地图点、回放或逐设备详情 |
| `GET` | `/api/bigscreen/panorama/maps/{mapId}/resources` | 当前地图渲染资源：点位、关联设备 ID 与固定摄像头；首屏默认地图和用户切图时调用 |
| `GET` | `/api/bigscreen/panorama/maps/{mapId}/task-routes` | 当前地图关联任务的路径点；不加载任务回放或设备任务详情 |
| `GET` | `/api/bigscreen/panorama/devices/{deviceId}` | 按装备序列号查询授权设备详情；只补查目标组件，不加载任务回放；弹窗选中时调用 |
| `GET` | `/api/bigscreen/panorama/tasks` | 查询当前任务快照 |
| `GET` | `/api/bigscreen/panorama/tasks/{taskId}` | 用户打开任务时查询单个任务完整详情（含回放和设备任务明细） |
| `GET` | `/api/bigscreen/panorama/tasks/{taskId}/fixed-cameras` | 实时监控任务卡展开时按需查询任务关联固定摄像头；只返回安全视频源标识，不创建视频会话 |
| `GET` | `/api/bigscreen/panorama/alarms` | 查询告警分组快照 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/handled` | 处置普通告警 |
| `GET` | `/api/bigscreen/panorama/alarms/actionable-workflow` | 查询当前用户可处理的工作流告警 |
| `POST` | `/api/bigscreen/panorama/alarms/{alarmId}/handle-and-continue` | 处置告警并继续对应工作流 |
| `GET` | `/api/bigscreen/access-control/me` | 代理当前登录用户的管理端数据权限；下游失败时拒绝访问，不返回默认权限 |

`actionable-workflow` 返回的每条告警均带有 `workflowActionable: true`，前端以该标识选择 `handle-and-continue`，不再根据工作流实例或人工任务字段是否存在进行推断。

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

设备对外 `status` 仅为 `online/fault/offline`。机器人在线状态来自本项目 Control
`/api/control/robots/registry`：收到合法、非 retained 的边缘状态后立即为 `online` 或 `fault`，
连续 30 秒未收到边缘状态后由 Control 标记为 `offline`；Management Control 的
`DeviceRealtimeStatus.onlineStatus` 不参与状态判定；外部实时状态接口只用于定位、告警和任务等未迁移字段。机器人条目同时返回
`statusChangedAt`（服务端状态变更时间），前端只允许较新的状态覆盖当前值；注册表中不存在的设备按
`offline` 返回。

机器人电量、速度、模式统一由本项目 Control 注册表取得，并返回独立的 `runtimeUpdatedAt`。
Overview、设备详情与 WebSocket 使用相同运行态源，前端按该时间比较运行态新旧，不能用
仅在在线状态变化时更新的 `statusChangedAt` 来判断速度和模式的新旧。缺值保留 `null`，
不补零电量、零速度或默认模式。组件数量与详情调用规则见字段来源文档 3.3 节。

装备弹窗打开时全部装备展示字段由设备详情初始化；名称、类型、型号、上装数量和固定摄像头位置
在本次打开期间保持不变，电量、速度、控制模式与在线状态从共享状态按上述版本更新，不用 Overview
回填静态档案。重连不清空或重查详情；任务路径及视频会话仍复用共享链路。请求取消与失败重试
规则见字段来源文档第 4 节。Overview 只保留地图与任务列表所需设备摘要，静态档案仍以按需详情为准。

固定摄像头 `status=online` 仅在配置启用且完整、Gateway
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
`TASK_EXECUTOR_SATURATED`、`TASK_PAGINATION_LIMIT`、`TASK_PAGINATION_NO_PROGRESS`、`TASK_INVALID_RESPONSE`、
`WORKFLOW_INSTANCE_NOT_FOUND` 和 `WORKFLOW_DEFINITION_NOT_FOUND`。401/403 不进入降级响应。

### 2.1 按需读取时序与响应边界

页面首屏先请求 `overview`；若默认地图为 SLAM，再并行请求该地图的 `resources` 和 `task-routes`。用户切换
SLAM 地图时，仅请求目标 `mapId` 的这两个资源；用户打开任务视频时，才请求 `tasks/{taskId}`。实时监控页仅在
用户展开某张任务卡的“固定摄像头”列表时，请求 `tasks/{taskId}/fixed-cameras`。前端不应在首屏、定时刷新或切图时
预取任务回放、设备任务明细、任务固定摄像头或非当前地图点位。

Token 续期、WebSocket 重连及授权变化触发 Overview 刷新时，应保留当前仍有效的地图选择，
不得每次重选列表第一张。首页/实时监控的底图、工具栏与任务筛选共享 `globalMapId`；
用户在刷新期间切图时，以最新选择加载资源。所选地图确实不在新列表时才使用默认规则；
退出登录清空选择和地图资源，不跨账号保留。

Overview 的地图列表查询失败不再降级为 `map=[]`：地图读取超时、HTTP 错误、空响应或并发
许可耗尽返回 503，401/403 保持原状态；成功且确实无地图才返回空列表。普通刷新失败时前端
保留当前数据，权限集合明确变化时仍先清空并重新获取，禁止借保留选择恢复已失权数据。

`resources` 响应为 `{serverTime, mapId, points, deviceIds, fixedCamares}`，表示当前地图渲染所需的按需资源，其中
`points` 为当前地图点位，
`deviceIds` 为按设备关联任务的管理端地图 ID 与当前地图匹配的 `robotId` 数组。边缘端实时定位的 SLAM 地图 ID
不作为管理端地图归属依据；未关联任务地图的设备不出现在任一地图渲染资源响应中。完整设备对象仅使用
`overview.devices[]`，不得在 `resources` 重复下发；
`fixedCamares` 保持现有字段拼写，表示当前地图固定摄像头。
`task-routes` 响应为
`{serverTime, mapId, items, dataQuality}`；每个 `items[]` 包含 `taskId`、`workflowInstanceId`、`mapId` 和
`pathPoints`。Overview 的 `tasks[]` 不返回空的 `pathPoints` 占位，前端只把 `task-routes.items[]` 写入路径状态。
`tasks/{taskId}` 响应为 `{serverTime, task, dataQuality}`；找不到任务时 `task=null`，不以
伪造任务替代。`tasks/{taskId}/fixed-cameras` 响应为 `{serverTime, taskId, items}`，每个 `items[]` 只包含
`cameraId`、`name`、`sourceType=FIXED_CAMERA`、`sourceId` 和 `defaultQuality`。其来源是 Management
`task-workflow-plans/{taskId}/fixed-cameras`，覆盖该工作流计划及其依赖工作流关联路径的已启用摄像头并去重；
不得使用单一 `pathId` 推导，也不得向浏览器返回 RTSP 地址、账号、密码或会话凭据。用户选择一个 `sourceId` 后，
前端复用既有 `POST /api/bigscreen/control/fixed-cameras/{sourceId}/video/start` 创建会话；再次取消选择时停止该会话。

为抑制同一用户高频刷新，BFF 对同一 JWT `sub` 合并在途 `overview` 并仅复用 5 秒内的成功结果；失败结果
不缓存。地图场景、地图任务路径和可处置工作流告警按同一用户合并并成功缓存 3 秒。Management 通用资源
请求使用独立连接/读取时限（默认各 1000/1500 ms）及单实例公平并发上限（默认 16）；任务请求继续使用
独立的默认 1000/1500 ms、8 并发边界。缓存不跨用户共享，也不改变 401/403 语义。

固定摄像头目录租约只由 WebSocket 权限快照加载/续期链路同步；Overview 只读取摄像头展示与健康数据，
不再重复向 Control 写目录租约。今日告警与未处理告警仍属于首屏数据，但两次 Management 查询并行执行。
任务计划和任务实例使用每页 100 条的完整分页，并在总数满足、末页不足或分页无进展时立即停止。

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

`robot.state` 只按顶层 `data.robotId` 校验机器人数据权限；`data.cameras[]`
及上装状态内的 `cameraId` 表示机器人本体相机，继承该机器人权限，不与
Management 固定摄像头授权集合比较。固定摄像头视频事件使用
`sourceType=FIXED_CAMERA` 和 `sourceId=cameraId` 识别与授权。浏览器上行消息显式携带
`cameraId` 时仍执行固定摄像头预校验，不因上述事件规则放宽。

授权快照最大陈旧时间为 30 秒。Management 对设备或固定摄像头查询返回 `403` 时表示对应
查看权限已撤销，该类授权集合按空集更新；`401` 或握手 JWT 到达 `exp` 时使用 `4001` 关闭；
超时、5xx 或异常响应导致初始加载失败或快照过期时使用 `4003` 关闭。资源集合发生变化时连接
保持有效，BFF 发送以下通知，调用方必须重新请求 `/api/bigscreen/panorama/overview`，并以响应
完整替换旧设备集合：

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

重新请求 Overview 时，Management 设备或固定摄像头列表返回 `403` 分别表示该类查看权限已撤销；
BFF 对该类资源返回空集合并继续组装其余有权数据，因此真实撤权不会把 Overview 转换为 `502`。
`401`、超时、5xx、异常响应，以及地图、任务等其他资源的 `403` 仍按原失败语义返回。

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
| `management.task.invalidated` | 300ms 去抖后通过独立有界 I/O 通道重查任务计划/实例摘要并推送变化项；失败或仍处于 `PREPARING` 时按 1/2/4/8 秒最多复查 4 次，不占用全景页面查询线程，也不加载回放、路径和设备任务明细                                                                                             |
| alarm 变更类事件 | 立即转换为 `panorama.alarm.changed`，无真实上游事件时不生成模拟告警                                                                                                                                                         |
| `management.alarm.invalidated` | 300ms 去抖后重查告警权威快照，只推送变化项                                                                                                                                                                               |
| 设备、任务、告警或机器人在线状态变化 | 500ms 去抖后按事件类型只重算受影响统计块（设备/任务/告警，各块 3 秒 TTL 缓存按用户隔离），仅在快照变化时推送 `panorama.stats.changed`                                                                                                                |

当前代码仍对没有定位的 `test111`、`SN005`、`SN006` 生成硬编码演示位置事件。它不是管理端真实位置，也不是通用兜底；生产验收不得把这些事件作为真实定位依据。其他机器人无定位时不补位置事件。若上游 WebSocket 不可用，连接仍可建立，但不会收到上游动态事件，也不会凭空生成业务快照。
