# 固定监控摄像头 RTMP 直推实时视频设计说明书

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 评审中 |
| 方案日期 | 2026-09-16 |
| 适用模块 | Management Service、Media Service、Control Service、`bigscreen-bff`、LiveKit Ingress、管理端前端、`robot-ui`、部署模块 |
| 关联文档 | [固定监控摄像头实时视频设计说明书](固定监控摄像头实时视频设计说明书.md)、[固定监控摄像头 RTMP 直推开发实施说明书](固定监控摄像头RTMP直推开发实施说明书.md) |

## 1. 背景与目标

现有固定摄像头采用现场 `fixed-camera-gateway` 拉取 RTSP 并发布到 LiveKit。部分摄像头或 NVR
具备主动 RTMP 推流能力，需要支持设备直接向中心侧 LiveKit Ingress 推流，在用户现场不部署
Gateway 的情况下仍可复用现有大屏实时视频能力。

本方案目标：

1. 固定摄像头业务档案通过现有 `protocolType` 区分 `RTSP` 与 `RTMP`，不增加第二个业务接入模式字段。
2. RTMP 仅承担媒体传输，地图、任务、路径和装备关系继续以 `cameraId` 为业务关联键。
3. 复用现有 `VideoSession`、LiveKit Room/Track、播放组件、截图和录像能力。
4. RTMP 摄像头正常创建、播放、释放和恢复不依赖 Gateway，也不发送固定摄像头 MQTT 指令；仅从
   RTSP 切换为 RTMP 或删除 RTSP 摄像头时允许发送幂等 stop。
5. 推流凭证只在创建或轮换时返回给授权管理员，不进入普通播放和设备查询接口。

首期不包含 RTMPS、双 Ingress 无缝轮换、音频播放、新的清晰度切换界面、新的任务/路径关联、
新的截图/录像链路，以及调试或模拟接口。

## 2. 核心决策

| 决策项 | 正式方案 |
| --- | --- |
| 协议建模 | 复用 `protocolType=RTSP/RTMP`，不增加 `ingestMode` |
| 运行态建模 | Media 持久化 `publisher_mode`，禁止用 `ingress_id` 推断发布方式 |
| 首期部署前提 | Management 单实例；Media、Control 在维护窗口内整体升级，禁止新旧版本混跑 |
| 并发与崩溃恢复 | Management 使用实例内 cameraId keyed lock、少量持久化状态；发布模式与 Ingress CRUD 分别使用独立 revision fencing |
| 媒体安全门禁 | Media 在 Runtime 悲观锁内校验预期发布方式和 revision；Control 响应字段只用于观测 |
| RTMP 输入 | 每台摄像头一个 LiveKit Ingress，只推一路视频 |
| 清晰度 | Ingress 转换为三层 Simulcast；首期大屏不开放清晰度入口 |
| 音频 | 关闭 |
| 推流生命周期 | 摄像头或 NVR 持续主动推流，观看行为不启停推流 |
| Room | `media.fixed.{cameraId}.visible.main` |
| Publisher | `fixed-camera:{cameraId}` |
| Track 名称 | `camera` |
| VideoSession 清晰度 | 统一为 `main` |
| 推流配置入口 | 普通 Ingress CRUD 由 Management 直接调用 Media；协议切换和 RTSP 删除由 Control 协同收口旧发布端 |
| 用户设备配置 | 摄像头“服务器地址”填写完整 `pushUrl` |
| 业务关联 | 全部沿用 `cameraId`，不从地址、Key 或来源 IP 推断 |

未来开放统一清晰度入口时：机器人和 RTSP 固定摄像头仍通过主、子输入源切换实现；RTMP 固定摄像头
在同一 Track 的 Simulcast 层之间切换。两种实现可以在前端呈现为统一的清晰度控件，但后端语义不同。

## 3. 总体架构

```text
摄像头或 NVR
  -> rtmp://{public-host}:1935/live/{streamKey}
  -> LiveKit Ingress Worker
  -> H264_1080P_30FPS_3_LAYERS
  -> LiveKit Room / Participant / Track
  -> 现有 VideoSession 与 Viewer Token
  -> 大屏 LiveKit 播放组件

Management Service
  -> 固定摄像头档案、地图位置、启用状态
  -> 调用 Media 创建、查询、轮换、撤销推流配置

Media Service
  -> LiveKit Ingress 生命周期
  -> media_source_runtime、Room/Track 事实、VideoSession、录像

Control Service
  -> 统一播放入口和会话编排
  -> RTSP 分支发布 Gateway MQTT
  -> RTMP 分支只使用已存在的 Ingress Track
  -> RTSP/RTMP 协议切换或 RTSP 删除时协调 Media 和 Gateway 停止旧发布端

bigscreen-bff
  -> 聚合 Management、Control 和 Media 已归一化的设备状态
```

LiveKit Ingress Worker 是独立媒体进程，与 LiveKit Server 使用相同的 API Key、API Secret 和 Redis。
Java 服务不接收 RTMP 数据，也不引入 FFmpeg 转码链路。

## 4. 业务关联

### 4.1 权威关联键

`cameraId` 是媒体入口与业务数据之间唯一权威关联键：

```text
ingressId -> cameraId
cameraId -> 固定摄像头档案
cameraId -> mapId + coordinateX + coordinateY + headingYaw
任务计划 -> 工作流版本 -> 路径 -> path_fixed_camera -> cameraId
```

RTMP URL、Stream Key、来源 IP 和视频内容不得作为地图、任务或路径的关联依据。

### 4.2 现有关系复用

- 地图继续使用摄像头档案的 `mapId` 和坐标字段。
- 任务与路径继续使用现有工作流、路径及 `path_fixed_camera` 关系，不新增 RTMP 专用关系。
- 固定摄像头与机器人仍是大屏装备列表中的同级设备，不建立机器人从属关系。
- RTMP 不改变摄像头在装备列表、地图弹窗、路径或任务监控中的业务身份。

## 5. Management 设计

### 5.1 摄像头档案

复用现有固定摄像头增删改查接口和数据结构，仅扩展 `protocolType` 支持 `RTMP`：

| 字段 | RTSP | RTMP |
| --- | --- | --- |
| `protocolType` | `RTSP` | `RTMP` |
| `mainStreamUrl` | 必填 | `null` |
| `subStreamUrl` | 可选 | `null` |
| `username`、`password` | 按设备需要 | `null` |
| 地图、位置、启用状态 | 沿用现状 | 沿用现状 |

Management 固定摄像头表不保存 `ingressId`、Stream Key 或完整 `pushUrl`。首期明确采用单 Management
实例，只保存协议切换和删除必需的两个编排事实：

- `media_transition_state`：`STABLE`、`SWITCHING_TO_RTMP`、`SWITCHING_TO_RTSP`、`DELETING`。
- `publisher_revision`：只在协议切换和删除收口时递增，传递给 Control 和 Media，作为发布模式 generation。
- `ingress_operation_revision`：只在 Ingress 创建、轮换、撤销时递增，传递给 Media，拒绝迟到的配置操作；
  不参与播放门禁。

同一进程内所有固定摄像头变更使用可回收的 cameraId keyed lock 串行化。创建、轮换、撤销配置不进入
持久化状态机，由 Media Runtime 悲观锁、幂等接口、Ingress 对账和 revision fencing 保证安全。协议切换和
删除失败时保留目标 state；服务重启后由用户重提相同请求，从头执行完整幂等流程。远程调用期间不持有
Management 数据库事务。不增加 `version/@Version`、phase 或持久化错误字段。

### 5.2 管理端页面

保持现有新增、查看、编辑页面结构和权限：

- 新增或编辑时选择 `protocolType`。
- 选择 `RTMP` 时隐藏 RTSP 地址、用户名和密码字段。
- 新增页面点击一次“保存”：先保存摄像头档案，再自动调用“创建推流配置”。
- 推流配置创建失败时保留已保存档案，并在编辑页面提供重试，不回滚摄像头记录。
- 编辑页面提供创建、轮换、撤销推流配置操作。
- 查看模式保持现有只读 UI，不新增独立详情页。
- 列表保持查看和编辑按钮，不要求增加删除按钮；后端现有删除接口继续保留。
- 摄像头只有一个“服务器地址”输入框时，管理员填写 Media 返回的完整 `pushUrl`。

### 5.3 服务调用与身份

Management 的 Ingress 创建、查询、轮换和撤销直接调用 Media，复用现有 `MediaServicePort`
和 `DirectMediaServiceClient`。在 `RTSP <-> RTMP` 协议切换以及删除 RTSP 摄像头时，Management 调用
Control 的内部编排接口，由 Control 协调 Media 运行态收口和 Gateway MQTT；普通 Ingress CRUD 不经 Control。

内部调用沿用 Management 当前的服务身份头：

```text
X-User-Id: manager-service
X-Roles: MEDIA_OPERATOR
X-Org-Id: 使用现有服务配置；当前配置为空时不伪造前端组织信息
```

不透传浏览器提交的用户身份头。前端权限仍由 Management 的固定摄像头权限体系负责，Media 只信任
受信服务身份。

### 5.4 权限边界

不新增权限码。publish-config 的 GET、POST、PUT、DELETE 必须分别命中精确路由，权限为：查看、
创建或编辑任一、编辑、编辑；四种操作都继续执行 cameraId 对象数据权限校验。现有单权限规则扩展为
`anyOf` 表达后，其他路由仍按单元素权限集合处理。不得依赖无法匹配嵌套路径的现有 `/*` 通配规则。

## 6. 推流配置接口

### 6.1 Management 对前端接口

```http
POST   /api/v1/management/fixed-cameras/{cameraId}/publish-config
GET    /api/v1/management/fixed-cameras/{cameraId}/publish-config
PUT    /api/v1/management/fixed-cameras/{cameraId}/publish-config
DELETE /api/v1/management/fixed-cameras/{cameraId}/publish-config
```

语义：

| 方法 | 语义 |
| --- | --- |
| `POST` | 幂等创建；已有配置时不重复创建 |
| `GET` | 查询脱敏状态，不返回可用 Stream Key 或完整 `pushUrl` |
| `PUT` | 轮换凭证，返回新的完整配置 |
| `DELETE` | 幂等撤销配置 |

创建和轮换成功响应示例：

```json
{
  "cameraId": "2092258082746281985",
  "protocolType": "RTMP",
  "pushUrl": "rtmp://<public-host>:1935/live/RT_x7Kp9mQ2vN4sAbCd",
  "configured": true
}
```

`pushUrl` 必须由 LiveKit Ingress 返回的权威 URL 与 Key 组合产生，不在代码中硬编码公网 IP。
完整凭证只在创建或轮换响应中出现一次；GET 只返回脱敏结果。凭证遗失时执行轮换。

### 6.2 Media 内部接口

```http
POST   /internal/media/fixed-camera-ingresses
GET    /internal/media/fixed-camera-ingresses/{cameraId}
PUT    /internal/media/fixed-camera-ingresses/{cameraId}
DELETE /internal/media/fixed-camera-ingresses/{cameraId}
POST   /internal/media/fixed-camera-ingresses/status-query
```

创建请求只需要稳定业务主键：

```json
{
  "cameraId": "2092258082746281985"
}
```

批量状态请求：

```json
{
  "cameraIds": ["2092258082746281985"]
}
```

Media 不读取 Management 数据库，也不处理地图、任务或路径关系。

### 6.3 业务错误码

| 错误码 | 含义 |
| --- | --- |
| `INGRESS_DISABLED` | 当前环境未启用 LiveKit Ingress |
| `FIXED_CAMERA_NOT_FOUND` | 摄像头不存在 |
| `FIXED_CAMERA_PROTOCOL_NOT_RTMP` | 摄像头协议不是 RTMP |
| `FIXED_CAMERA_INGRESS_NOT_CONFIGURED` | 尚未创建推流配置 |
| `FIXED_CAMERA_INGRESS_UNAVAILABLE` | Ingress 服务不可用 |
| `FIXED_CAMERA_INGRESS_BUSY` | Ingress 管理并发许可已满，客户端可重试 |
| `FIXED_CAMERA_STREAM_OFFLINE` | 已配置但没有有效 Track |
| `FIXED_CAMERA_STREAM_STATUS_UNKNOWN` | 状态超过容忍时间且无法确认 |

Management 负责校验摄像头存在且 `protocolType=RTMP`；Media 对内部资源状态和 LiveKit 错误负责。

## 7. Media 数据模型

复用现有 `media_source_runtime`，不新建固定摄像头 Ingress 映射表。增加以下必要运行态字段：

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `publisher_mode` | `varchar(32)` | 非空 | `DEVICE_CLIENT` / `FIXED_CAMERA_GATEWAY` / `LIVEKIT_INGRESS` |
| `publisher_revision` | `bigint` | 非空 | Media 已接受的最新发布模式 generation，只参与播放与模式切换门禁 |
| `ingress_operation_revision` | `bigint` | 非空 | Media 已接纳的最高 Ingress CRUD fencing token，不参与播放门禁 |
| `accepted_ingress_operation` | `varchar(16)` | 可空 | 最高 token 对应的 `CREATE/ROTATE/REVOKE`，用于同 revision 恢复与对账 |
| `ingress_id` | `varchar(128)` | 可空、唯一 | 对应 LiveKit Ingress ID |
| `last_stream_status` | `varchar(16)` | 可空 | 上次成功核验后的 `ONLINE/OFFLINE/UNKNOWN` |
| `last_reason_code` | `varchar(64)` | 可空 | 上次成功核验后的归一化原因 |
| `last_verified_at` | `timestamp` | 可空 | 最近一次成功核验 LiveKit 状态的服务端时间 |

现有 `source_type=FIXED_CAMERA`、`source_id=cameraId`、`room_name`、`publisher_identity`、
`publisher_participant_sid`、`track_sid`、`track_name` 和 `last_media_at` 字段继续承担运行时事实记录。

`publisher_mode` 是生命周期分流的稳定事实：即使 Ingress 被撤销、轮换失败或暂时不存在，
RTMP 摄像头仍保持 `LIVEKIT_INGRESS`。`ingress_id` 只表示当前 Ingress 资源是否存在，不得用于
判断该向 Gateway 发送指令还是执行 Ingress 逻辑。

Media 对发布模式操作只接受 `publisherRevision >= publisher_revision`，播放只校验发布模式和
`publisher_revision`。Ingress CRUD 独立比较 `ingressOperationRevision`，不得推进 `publisher_revision`，
也不得导致正常播放因配置操作失败而被拒绝。大于当前值的 Ingress 操作必须在
调用 LiveKit 前，通过独立短事务持久化 `ingress_operation_revision` 和
`accepted_ingress_operation`；小于已接纳 token 的请求始终拒绝，不因较新操作执行失败而回退。

相同 revision 且操作类型相同时不能盲目 no-op，必须依据 Runtime、ListIngress 和资源 metadata
对账目标状态：`CREATE` 可保留 Runtime `ingress_id` 已权威映射且 LiveKit 仍存在的单一资源，
也可恢复唯一的当前 token/type 资源；`ROTATE` 要求存在且只存在一个当前 token/type 资源；
`REVOKE` 要求已无平台托管 Ingress。达到目标状态后幂等成功；未达到时从当前资源事实继续补齐。

接纳事务提交后，Media 另开执行事务悲观锁定 Runtime，在 5 秒 LiveKit 总超时内持有该行锁完成
同 cameraId 的对账和外部副作用，从而串行化同 token 重试、更高 token 请求以及并发请求线程。
LiveKit 调用返回后、执行事务提交前必须再次校验 token/type。HTTP 超时或进程崩溃只回滚执行事务，
已接纳 token/type 不回退。如果 LiveKit 在客户端超时后才生成旧资源，不得回写旧结果；能获取资源 ID 时
立即补偿删除，结果不确定或补偿失败时由统一对账任务继续清理。

Media 管理端点还必须校验当前模式：Ingress 创建、轮换只允许 `LIVEKIT_INGRESS`；撤销在
`LIVEKIT_INGRESS` 下执行，在已经切到 `FIXED_CAMERA_GATEWAY` 且无 Ingress 时安全 no-op；`quiesce` 只允许
`FIXED_CAMERA_GATEWAY`。`publisher-mode` 同 revision 且已为目标模式时无副作用成功；大于当前
revision 且已为目标模式时，只持久化新的 `publisher_revision`。两种情况都不得清空目标
Track、关闭新会话或重复执行旧发布端清理。

数据库不保存明文 Stream Key、完整 `pushUrl`、LiveKit API Secret 或摄像头密码。

## 8. LiveKit Ingress 配置

每台 RTMP 摄像头固定创建一个 Ingress：

| 配置项 | 值 |
| --- | --- |
| Ingress 名称 | `fixed-camera-{cameraId}` |
| Room | `media.fixed.{cameraId}.visible.main` |
| Participant Identity | `fixed-camera:{cameraId}` |
| Track Name | `camera` |
| Track Source | `CAMERA` |
| Video Preset | `H264_1080P_30FPS_3_LAYERS` |
| Audio | 不启用 |

Ingress metadata 至少包含 `managedBy`、`cameraId`、`operationRevision`、`operationType` 和
`createdAtEpochSeconds`。`operationRevision/operationType` 必须来自已接纳的 Runtime fencing 事实，
用于识别同 token 重试结果、过期外部副作用和孤儿资源；不得写入 Stream Key 或其他凭证。

摄像头侧推荐编码：

| 项目 | 推荐值 |
| --- | --- |
| 分辨率 | `1920x1080` |
| 帧率 | `30 fps` |
| 编码 | `H.264` |
| 码率 | `3.5-4 Mbps` |
| 码率类型 | `CBR` |
| I 帧间隔 | `30` |
| 音频 | 关闭 |

现有设备的 `2560x1440 / 25 fps / VBR / 4 Mbps / I帧间隔50` 可以工作，但会增加转码与上行成本，
不作为首期推荐配置。

首期使用 RTMP：

```text
rtmp://{public-host}:1935/live/{streamKey}
```

RTMPS 留作后续增强，不因此新增首期端口和证书配置。

## 9. 播放与 VideoSession

大屏继续使用现有播放接口：

```http
POST /api/control/fixed-cameras/{cameraId}/video/start
```

Control 在会话请求中传入 Management 读取到的 `expectedPublisherMode` 和 `expectedPublisherRevision`，
Media 在 Runtime 悲观锁内完成原子校验和会话创建。只有
`RTSP + FIXED_CAMERA_GATEWAY` 或 `RTMP + LIVEKIT_INGRESS` 这两种组合可以继续；两者不一致表示
协议正在切换或上一步失败，应返回可重试的切换中状态，不发 MQTT，也不生成 Publisher Token。

Control 在收到会话响应后仍可记录 `publisherMode/publisherRevision` 用于观测，但不能依赖该响应
作为唯一门禁。后续 `requestClientStart` 必须再次按 `runtime -> session` 顺序加锁并核对模式：
`DEVICE_CLIENT` 只允许机器人，`FIXED_CAMERA_GATEWAY` 只允许 RTSP 固定摄像头，
`LIVEKIT_INGRESS` 禁止签发 Publisher Token 或生成客户端启动命令。

### 9.1 RTSP

保持现有流程：创建或复用会话，选择 RTSP 主/子码流，通过 MQTT 通知 Gateway 拉流并发布。

### 9.2 RTMP

1. 创建或复用 `sourceType=FIXED_CAMERA`、`sourceId=cameraId`、`quality=main` 的 VideoSession。
2. 查询 Media 已存在的 Runtime 和 Track。
3. Track 已发布时，会话可直接进入 `STREAMING` 并签发 Viewer Token。
4. Ingress 已配置但没有 Track 时返回离线状态，不向 Gateway 发布任何 MQTT 指令。
5. 不生成 Publisher Token，不调用 `start`、`stop`、`restart` 或 `switch-channel`。
6. Viewer 释放只结束观看关系，不删除 Ingress、Room，也不要求摄像头停止推流。

RTMP Track 丢失时，活动会话进入 `INTERRUPTED`，前端保留播放器并等待恢复。摄像头重新发布 Track 后，
现有会话自动恢复，不创建新的业务会话。Viewer 刷新只重建观看订阅，不重启媒体源。

## 10. Webhook 与状态对账

复用现有 LiveKit Webhook 验签、Room 二次核验和 Track 对账能力：

1. 创建 Ingress 前先创建或更新对应 `media_source_runtime`。
2. 收到 Participant/Track 事件后按固定 Identity 和 Room 回写 `track_sid` 与状态。
3. 扩展现有 LiveKit Track 对账任务，每 5 秒同时扫描活动会话 Runtime 和
   `publisher_mode=LIVEKIT_INGRESS` 的 Runtime，继续共用现有数据库调度租约。
4. 新建 VideoSession 时复制当前 Runtime 的有效 Track，避免等待下一次 Webhook。
5. 所有时间判断使用 Media 服务端时间，不使用摄像头或浏览器时间。

启动和周期漂移处理：

- 数据库有 `ingress_id`、LiveKit 无对应资源：清空映射并标记未配置，不自动重建凭证。
- LiveKit 资源 metadata 的 operation revision 小于 Runtime 已接纳 token，且不是 `CREATE` 已保留的
  Runtime 当前权威 `ingress_id`：视为过期资源并删除，不得重新映射到 Runtime。
- LiveKit 存在当前 token/type 的精确匹配资源、但数据库无映射：按本次目标状态恢复映射，
  但不恢复或再次暴露旧 Stream Key。
- LiveKit 存在带平台归属 metadata、但不对应 Runtime 已接纳 token/type 的 Ingress：
  超过创建保护期后自动删除；已明确为过期 token 的资源无需等待保护期。删除失败由
  同一对账任务持续重试。不删除缺少平台归属标识的外部资源。
- 创建结果超时或不确定时，必须先按确定性名称、Room、Identity 和归属 metadata 执行
  ListIngress 对账。数据库未映射的精确匹配资源先撤销再重建，不得直接创建第二个 Ingress。
- 多个平台归属资源匹配同一 `cameraId` 时必须收敛为一个；未收敛前创建接口返回可重试失败。
- LiveKit 查询失败：短期保留上次状态，超过 15 秒后归一为 `UNKNOWN`。

## 11. 配置生命周期

### 11.1 创建

1. 保存 RTMP 摄像头档案并获得稳定 `cameraId`。
2. Management 调用 Media 创建 Ingress。
3. Media 先把 Runtime 的 `publisher_mode` 持久化为 `LIVEKIT_INGRESS`，再调用 LiveKit 创建 Ingress，
   并保存 `ingress_id`。
4. Management 将完整 `pushUrl` 返回给当前授权管理员一次。
5. 管理员把完整 `pushUrl` 填入摄像头唯一的“服务器地址”输入框。

### 11.2 轮换

轮换用于凭证泄露或遗失。首期允许旧推流短暂中断：删除旧 Ingress、清空 `ingress_id`、
创建新 Ingress 并更新映射，但 `publisher_mode` 全程保持 `LIVEKIT_INGRESS`。轮换失败时不得进入
Gateway 恢复或空闲停止分支。现有 VideoSession 和 Viewer 保留，摄像头使用新地址恢复推流后自动续播。

### 11.3 撤销

撤销时删除 LiveKit Ingress、清空 Runtime 的 Ingress 和 Track 状态，并结束相关活动会话；摄像头档案保留。
只要 Management 协议仍为 RTMP，`publisher_mode` 仍保持 `LIVEKIT_INGRESS`。重复撤销返回成功。

### 11.4 启用与禁用

管理端禁用只修改现有 `enabled` 字段，不调用 Media，不删除 Ingress，也不轮换凭证：

- 禁用后拒绝新的播放请求。
- 已存在的 Viewer 和推流按现有会话生命周期自然结束。
- 再次启用后继续复用原 Ingress。

### 11.5 协议切换

协议切换采用简化持久化状态和向前收敛，不依赖分布式事务。每个步骤都携带当前
`publisher_revision`；涉及 Ingress 的步骤另携带 `ingress_operation_revision`，两类过期请求分别被拒绝。

`RTSP -> RTMP`：

1. Management 在 keyed lock 内从 `STABLE` 进入 `SWITCHING_TO_RTMP`，同时递增 publisher revision 和
   ingress operation revision，其他修改请求返回冲突。
2. Management 调用 Control，Control 携带 publisher revision 调用 Media 把该 `cameraId` 收口到 `LIVEKIT_INGRESS`。
3. Media 在 Runtime 锁内先核对 publisher revision，再持久化模式、关闭会话和录像；Control 发送幂等 stop 并确认旧 Track 退出。
4. Management 保存 `protocolType=RTMP`，再携带 ingress operation revision 幂等创建 Ingress。
5. 全部成功后以 state/publisher revision 条件更新回 `STABLE`。
6. 任一步骤失败时保留 state 和两类 revision；用户重提相同切换请求时复用它们，从第 2 步起完整幂等重试。

`RTMP -> RTSP`：

1. Management 在 keyed lock 内进入 `SWITCHING_TO_RTSP`，同时递增两类 revision。
2. Management 携带 ingress operation revision 撤销 Ingress，Media 关闭相关会话和录像。
3. Management 保存已校验的 RTSP 地址和 `protocolType=RTSP`。
4. Management 携带 publisher revision 调用 Control 把 Runtime 切换为 `FIXED_CAMERA_GATEWAY`，成功后按
   state/publisher revision 更新回 `STABLE`。
5. 失败时保留 state 和两类 revision，用户重提相同请求时从第 2 步完整幂等重试；完成前播放请求因非
   `STABLE` 或 publisher revision 不匹配而被拒绝。

RTMP 档案的名称、位置、备注等普通编辑不自动轮换凭证。

### 11.6 删除摄像头

后端保留现有 DELETE 能力，即使管理端列表当前没有删除按钮。删除时：

1. 执行现有地图、路径、任务等关联校验。
2. Management 在 keyed lock 内进入 `DELETING` 并递增 publisher revision；RTMP 同时递增 ingress operation
   revision，立即拒绝新播放、编辑和推流配置操作。
3. RTMP 先用 publisher revision 把既有 `LIVEKIT_INGRESS` Runtime 推进到新 generation，再用 ingress
   operation revision 删除 Ingress 并关闭会话/录像；RTSP 使用 publisher revision 通过 Control/Media
   关闭会话/录像、发送精确 stop。
4. 两种协议都必须确认固定 Participant/Track 已退出。Runtime 行保留，避免并发 Webhook 或历史引用失去落点。
5. 运行态清理失败时保留 `DELETING` 和两类 revision；再次提交删除时从运行态清理开始完整幂等重试。
6. 清理成功后删除摄像头档案，不允许“档案已删除但 Publisher 等待 TTL 自然结束”。

### 11.7 失败恢复

- Management 不主动扫描并自动续跑失败操作；用户重新提交相同协议切换或删除请求时，复用持久化
  state 和两类 revision，从流程起点完整幂等重试。
- 编辑页识别非 `STABLE` 状态并显示“媒体切换未完成，可重试”，保留原目标协议和表单数据。
- 恢复完成前拒绝普通编辑、启用/禁用、反向切换和播放。
- 不提供普通用户可调用的“强制改为 STABLE”接口，也不恢复明文 Stream Key。
- 长期失败由运维核对 Management state、两类 revision、Media Runtime、Ingress、Participant 和 Track 后执行
  受控恢复，并记录审计日志。

## 12. Gateway 边界

Gateway 摄像头目录只包含 `enabled=true` 且 `protocolType=RTSP` 的摄像头。RTMP 摄像头必须从以下
Gateway 逻辑中排除：

- 目录同步和本地缓存。
- RTSP 健康探测。
- 拉流进程启动、恢复和停止。
- MQTT `start`、`stop`、`restart` 指令。
- Gateway 在线状态和推流失败告警。

这保证 RTMP 直推不会被伪装成 RTSP Gateway 摄像头。

协议刚切换时，其他用户的旧目录租约最长可在现有 TTL 内保留 RTSP 记录。此过渡期允许 Gateway
保留旧目录和探测，但必须先主动停止旧 Publisher，并由 `publisher_mode` 确保 Control 不再下发
start/restart。TTL 到期后 RTMP 记录必须完全从 Gateway 目录消失。

## 13. 大屏状态模型

整体调用仍保持：

```text
robot-ui -> bigscreen-bff -> Control
                              -> Management 摄像头档案
                              -> Media RTMP 运行状态
```

BFF 不直接调用 Media，不保存推流状态。RTMP 状态归一化：

| Media 事实 | 配置状态 | 流状态 | 大屏可播放 |
| --- | --- | --- | --- |
| `publisher_mode=LIVEKIT_INGRESS` 且 `ingress_id` 为空 | `UNCONFIGURED` | `UNKNOWN / INGRESS_NOT_CONFIGURED` | 否 |
| `ingress_id` 非空且 `track_sid` 有效 | `CONFIGURED` | `AVAILABLE` | 是 |
| `ingress_id` 非空但无 Track | `CONFIGURED` | `UNAVAILABLE / RTMP_TRACK_MISSING` | 否 |
| 状态超过 15 秒无法核验 | `CONFIGURED` | `UNKNOWN / LIVEKIT_STATUS_STALE` | 否 |

RTMP 装备对外字段约定：

- `gatewayId=null`。
- `gatewayHealth=UNKNOWN`，原因为 `NOT_APPLICABLE`，BFF 和 UI 不把它作为离线依据。
- `clientId=livekit-ingress`。
- `defaultQuality=main`。
- `playable = enabled && configReady && streamStatus == AVAILABLE`。

RTMP 摄像头仍出现在装备列表、地图弹窗、路径/任务监控等现有入口，但不展示控制中心、控制器或 PTZ 操作。

## 14. 截图、录像与清晰度

### 14.1 截图

复用现有浏览器 Canvas 截图和文件上传链路，不增加服务端截图接口。

### 14.2 录像

复用现有基于 VideoSession `roomName + trackSid` 的 LiveKit Egress 录像链路。RTMP 不新建录像协议。
Track 更换时按现有行为结束当前录像，不在首期实现跨 Track 自动续录。

### 14.3 清晰度

首期保持大屏当前 UI，不新增清晰度按钮。Ingress 保留三层 Simulcast，浏览器与 LiveKit 可根据带宽自适应。
后续统一入口再分别接入 RTMP Simulcast 层切换和机器人/RTSP 主子输入源切换。

## 15. 部署与配置

### 15.1 Media 配置

```yaml
media:
  livekit:
    ingress-enabled: ${LIVEKIT_INGRESS_ENABLED:false}
    ingress-status-stale-seconds: ${LIVEKIT_INGRESS_STATUS_STALE_SECONDS:15}
    reconcile-delay-ms: ${LIVEKIT_RECONCILE_DELAY_MS:5000}
    ingress-admin-max-concurrency: ${LIVEKIT_INGRESS_MAX_CONCURRENCY:2}
    ingress-admin-permit-wait-ms: ${LIVEKIT_INGRESS_PERMIT_WAIT_MS:200}
    ingress-livekit-call-timeout-ms: ${LIVEKIT_INGRESS_CALL_TIMEOUT_MS:3000}
    ingress-admin-operation-timeout-ms: ${LIVEKIT_INGRESS_OPERATION_TIMEOUT_MS:5000}
```

复用现有 LiveKit 内部 URL、API Key、API Secret 和 Redis 配置。`ingress-enabled=false` 时，机器人和
RTSP 摄像头链路不受影响；Ingress POST/PUT 返回 `INGRESS_DISABLED`，GET、DELETE、状态查询、Webhook、
周期对账和孤儿清理继续工作，保证关闭新能力后仍可撤销已有资源。

Ingress 变更在打开数据库事务前获取每 Media 实例的全局并发许可。默认最多 2 个操作同时进入
持锁执行段，获取许可超过 200 ms 返回可重试 503。整个 Media 操作共享 5 秒 deadline，单次 LiveKit
HTTP 调用不超过 3 秒且不超过剩余预算。Hikari 必须满足
`maximumPoolSize >= max(4, ingressAdminMaxConcurrency * 4)`；默认并发数 2 要求连接池至少为 8，
配置不符合时 Media 拒绝启动。Management 调用该端点的专用超时为 8 秒，管理前端/API Gateway 不低于 12 秒。

### 15.2 Ingress Worker

- 使用与 LiveKit Server 相同的 API Key、API Secret 和 Redis。
- Worker 通过内部 WebSocket 地址连接 LiveKit。
- 对公网开放现有规划的 TCP `1935`，不要求增加 Java 服务端口。
- LiveKit Server、Ingress 和 Egress 作为同一兼容基线锁定具体镜像标签和 digest，禁止交付 `latest`。
- 同时准备 `amd64`、`arm64` 离线镜像。
- 不在 Java 容器内运行 Ingress 或媒体转码进程。

已核对的目标服务器为 16 vCPU、62 GB 内存、无 GPU、100 Mbps 网络。首批建议先验证 1-2 路，
再逐步扩展到 4 路，并在部署前清理根磁盘空间。CPU 转码容量必须通过目标码率和并发压测确认。

## 16. 安全与可观测性

### 16.1 安全

- Stream Key 使用不可预测随机值，不使用 `cameraId`、序列号或地图 ID。
- 完整凭证只在创建和轮换响应中返回，不写 Local Storage，不进入普通列表和详情响应。
- 日志禁止记录 Stream Key、完整 `pushUrl`、API Secret 和摄像头密码。
- Management 对外接口沿用固定摄像头现有权限；Media 内部接口只接受受信服务身份。

### 16.2 日志

正式日志包含：`cameraId`、`ingressId`、操作类型、结果、耗时和脱敏错误类型。禁止输出凭证内容。

### 16.3 指标

至少包括：

- 已配置 Ingress 数量。
- 当前在线 Track 数量。
- 对账失败次数。
- 创建、轮换、撤销失败次数。
- Ingress 管理当前执行数、并发拒绝次数和执行耗时。
- 并发许可等待、LiveKit 各 API 调用和 Media 整体操作耗时。

指标标签不使用 `cameraId`，避免高基数。首期不增加 HTTP 调试接口、模拟接口或测试用兼容路径。

## 17. 开发边界

### 17.1 Management Service

- `protocolType` 增加 RTMP 校验分支。
- 复用现有 Media Client 增加推流配置调用。
- 新增推流配置 CRUD API 和协议切换、删除编排。
- 不保存推流密钥和 Ingress 映射。

### 17.2 Media Service

- 封装 LiveKit Ingress 创建、查询、轮换和删除。
- 扩展 `media_source_runtime` 的发布方式、Ingress 映射和最后状态字段及迁移脚本。
- 扩展 Webhook、现有统一定时对账、状态批量查询和 RTMP 会话衔接。
- 在 Ingress 变更打开事务前使用全局并发许可，保护共享 Hikari 连接池。
- 对平台归属的孤儿或重复 Ingress 执行可重试的自动收敛。
- 复用现有 Token、Room、Track、录像和文件能力。

### 17.3 Control Service

- 播放入口按 `protocolType` 分流。
- RTMP 正常创建、播放、释放、断流恢复和重启分支禁止发送 Gateway MQTT，复用 Media Runtime 和 VideoSession。
- 聚合 Management 档案与 Media RTMP 状态供 BFF 使用。
- `RTSP -> RTMP` 协议切换和 RTSP 删除时可下发幂等 Gateway stop，并确认旧 Track 退出。

### 17.4 bigscreen-bff 与 robot-ui

- 复用现有设备、地图、任务/路径及播放器入口。
- RTMP 状态忽略 Gateway 健康字段。
- 固定摄像头继续隐藏控制中心、控制器和 PTZ 操作。
- 首期不增加清晰度切换 UI。

### 17.5 部署

- 增加 LiveKit Ingress Worker 容器、配置和离线镜像。
- 开放并验证 TCP `1935` 的公网映射。
- 不新增 Gateway 部署要求。

## 18. 实施顺序

本章给出发布先后关系；具体接口 DTO、代码落点、状态机、事务补偿、测试清单和提交边界见
[固定监控摄像头 RTMP 直推开发实施说明书](固定监控摄像头RTMP直推开发实施说明书.md)。

1. 锁定并验证 LiveKit Server、Ingress、Egress 兼容版本，再部署 Ingress Worker 和 TCP `1935`。
2. 在维护窗口内停止旧 Control 和 Media，执行 Media 数据库迁移并整体发布新 Media、Control；禁止
   旧 Control + 新 Media 或新 Control + 旧 Media 混跑。
3. 发布 BFF 和大屏前端的兼容改动。
4. 发布 Management 后端推流配置接口。
5. 最后发布 Management 前端，避免页面先暴露尚不可用的配置入口。
6. 分别回归机器人、RTSP 固定摄像头和 RTMP 固定摄像头。

在线止损先隐藏并禁止管理端新增/轮换，再关闭 RTMP 开关；记录或撤销既有 Ingress 后才停止 Worker。
DELETE 和对账清理在开关关闭后仍可使用。在线止损不回滚 Media/Control 二进制。若必须回滚旧二进制，需在维护
窗口内先把 `publisher_mode` 改回可空，再把 Media、Control 作为同一版本单元整体回滚；不得保留无默认值
的非空约束后启动旧 Media，也不得用 `DEFAULT DEVICE_CLIENT` 掩盖兼容问题。

## 19. 验收标准

1. 新增 RTMP 摄像头一次保存即可获得完整 `pushUrl`；GET 不泄露可用凭证。
2. 摄像头只填写一个完整地址即可推流，Media 能识别对应 `cameraId`、Room 和 Track。
3. 大屏从装备列表、地图和既有任务/路径入口均能播放同一摄像头。
4. RTMP 正常创建、播放、释放、断流恢复和重启不产生 Gateway MQTT；
   仅 `RTSP -> RTMP` 切换和 RTSP 删除允许发送一次或多次幂等 stop。
5. 摄像头断流后页面进入中断状态，恢复同一推流后自动续播。
6. 轮换后旧地址失效，新地址可用；现有会话可在新 Track 发布后恢复。
7. 撤销后无法继续通过旧地址推流，重复撤销成功。
8. 禁用只影响新的业务播放，不删除 Ingress；重新启用后原配置仍可使用。
9. RTMP 摄像头不进入 Gateway 目录，不展示控制器和 PTZ 操作。
10. 截图与录像复用现有链路可用，机器人和 RTSP 摄像头无回归。
11. LiveKit 状态查询异常超过 15 秒后显示未知，不误报在线。
12. 日志、接口和数据库均不泄露 Stream Key、完整 `pushUrl` 或 API Secret。
13. 轮换或创建失败后 `publisher_mode` 仍为 `LIVEKIT_INGRESS`，不产生 Gateway MQTT，不删除错误 Room。
14. `RTSP -> RTMP` 切换前旧 Gateway Publisher 已停止，旧 Track 不会被识别为 RTMP Track。
15. CreateIngress 超时、服务重启和重复请求均不会留下多个有效的平台 Ingress。
16. 播放请求与双向协议切换并发时，Media 原子门禁保证不签发错误 Publisher Token、不启动双发布端。
17. Management 重启后保留切换/删除 state 和两类 revision；相同请求可完整幂等重试，过期请求无法修改 Media。
18. 删除 RTSP 摄像头前活动 Gateway Publisher 和 Track 已停止，不依赖目录 TTL 自然到期。
19. 非 `STABLE` 状态可由相同请求重试恢复，前端有明确入口，长期失败具有受控运维恢复流程。
20. 默认功能回滚不回退 Media/Control；旧二进制回滚前已演练可空迁移和整体版本回滚。
21. publish-config 失败不改变播放使用的 publisher generation，正常 Ingress 仍可播放。
22. 迟到轮换不能覆盖后续撤销；相同 revision 的不同操作类型被拒绝，相同操作根据
    Runtime 与 metadata 对账并补齐目标状态。
23. 旧 Ingress 外部调用在更高 token 接纳后才返回时，不回写过期结果；旧操作创建的资源
    由即时补偿和周期对账最终删除。
24. 关闭 Ingress 开关后 POST/PUT 禁止，GET/DELETE/对账可用，资源清理后才能停止 Worker。
25. Ingress 持锁执行事务受全局并发许可限制；LiveKit 慢请求压测下不耗尽 Hikari 连接池，
    机器人和 RTSP 的会话、Token、录像与释放路径无显著退化。
26. 机器人的会话新建/复用、主子码流切换、上线恢复、自动/手动重启、对讲升级、录像与空闲释放，
    以及 RTSP 固定摄像头开始/复用/恢复/停止/释放均通过共享路径回归。
27. 超时层级固定为许可等待 200 ms、单次 LiveKit 最多 3 秒、Media 总预算 5 秒、Management 专用客户端
    8 秒、管理前端/API Gateway 不低于 12 秒；边界慢请求不被外层提前超时。

## 20. 参考资料

- [LiveKit Ingress Overview](https://docs.livekit.io/home/ingress/overview/)
- [LiveKit Ingress Transcoding](https://docs.livekit.io/home/ingress/overview/#transcoding)
- [LiveKit Ingress GitHub](https://github.com/livekit/ingress)
