# 固定监控摄像头 RTMP 直推开发实施说明书

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 开发中，待全链路验收 |
| 基线日期 | 2026-09-17 |
| 上位设计 | [固定监控摄像头 RTMP 直推实时视频设计说明书](固定监控摄像头RTMP直推实时视频设计说明书.md) |
| 适用仓库 | `robot-mediaserver`、`eiop-management-service`、`eiop-admin-ui` |
| 实施原则 | 复用现有 `VideoSession + LiveKit + 大屏播放器`，只增加 RTMP Ingress 必要能力 |

## 1. 文档目的与效力

本文件把上位设计转换为开发、联调和验收契约。本轮评审项已关闭，LiveKit 版本兼容基线已于
2026-09-17 在目标 amd64 部署形态完成 LiveKit 兼容实测，当前代码已进入实施收口，尚待真实摄像头全链路验收。本文解决以下问题：

1. 各服务具体新增或修改哪些类、接口、字段和配置。
2. 创建、查询、轮换、撤销、禁用、协议切换和删除时如何处理事务与失败。
3. RTMP Track 如何接入现有 VideoSession、WebSocket、录像和大屏状态。
4. 哪些现有 RTSP、Gateway、机器人逻辑必须保持不变。
5. 开发完成后必须通过哪些测试，才能认为链路闭环。

实现与本文冲突时，以本文和上位设计为准；如开发中发现必须改变已确认边界，应先更新设计并重新评审，
不得在代码中自行引入第二套协议、兼容字段或临时调试接口。

上位设计第 18 章是部署实施顺序。本文件第 18 章提供更细的开发任务顺序和提交边界。

## 2. 当前代码基线

### 2.1 已有能力

| 模块 | 可直接复用的现状 |
| --- | --- |
| Media | `VideoSourceRuntime`、`VideoSession`、Room/Track 对账、Viewer Token、Webhook、Egress 录像 |
| Control | `/api/control/fixed-cameras/{cameraId}/video/start`、Management 数据权限、Media Client |
| BFF | 固定摄像头装备聚合、地图/任务/路径入口、Control 健康状态聚合 |
| robot-ui | 固定摄像头播放器、截图、录像、断流展示、控制入口隐藏 |
| Management | 固定摄像头 CRUD、地图和路径关系、`MediaServicePort`、`DirectMediaServiceClient` |
| Gateway | RTSP 目录、健康探测、MQTT 拉流和恢复 |
| 部署 | LiveKit Server、Redis、Egress、双架构离线镜像目录 |

### 2.2 当前限制

- Management 的 `FixedCameraProtocolType` 只有 `RTSP`。
- `SaveFixedCameraRequest.mainStreamUrl` 当前无条件 `@NotBlank`。
- Management 应用服务当前会把协议强制写成 `RTSP`。
- Control 当前把所有固定摄像头都当作 RTSP，并始终生成 Gateway start 命令。
- BFF 当前只认可 RTSP 配置，且固定摄像头状态依赖 Gateway 健康。
- Media 当前没有 Ingress CRUD，管理 Token 也没有 `ingressAdmin` grant。
- 空闲释放、自动恢复和手动重启当前会对所有固定摄像头执行 RTSP/Gateway 行为。

开发不得忽略这些分支，否则即使 RTMP 能推入 LiveKit，也会被既有 RTSP 生命周期误停止或误重启。

## 3. 不可变标识与常量

首期所有模块必须使用以下固定值，不允许自行拼出其他 Room 或第二路 Ingress：

| 项目 | 值 |
| --- | --- |
| `sourceType` | `FIXED_CAMERA` |
| `sourceId` | `{cameraId}` |
| `robotId` 兼容值 | `{cameraId}` |
| `deviceId` | `{cameraId}` |
| `channel` | `visible` |
| `quality` | `main` |
| `publisherMode` | `LIVEKIT_INGRESS` |
| Runtime 唯一键 | `FIXED_CAMERA:{cameraId}:{cameraId}:visible:main` |
| Room | `media.fixed.{cameraId}.visible.main` |
| Ingress 名称 | `fixed-camera-{cameraId}` |
| Participant Identity | `fixed-camera:{cameraId}` |
| Participant Name | 不传 |
| Video Track Name | `camera` |
| Track Source | `CAMERA` |
| Ingress Input | `RTMP_INPUT` |
| Video Preset | `H264_1080P_30FPS_3_LAYERS` |
| 音频 | 不创建 `audio` 配置 |

业务关联只认 `cameraId`。`ingressId`、Room、Participant、Stream Key、推流来源 IP 都不是地图、路径、
任务或组织关系的权威数据。

## 4. 数据库变更

### 4.1 Media 数据库

复用 `media_source_runtime`，新增脚本命名为：

```text
deploy/database/20260917-add-fixed-camera-ingress.sql
```

脚本内容：

```sql
ALTER TABLE media_source_runtime
    ADD COLUMN publisher_mode VARCHAR(32) NULL,
    ADD COLUMN publisher_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ingress_operation_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN accepted_ingress_operation VARCHAR(16) NULL,
    ADD COLUMN ingress_id VARCHAR(128) NULL,
    ADD COLUMN last_stream_status VARCHAR(16) NULL,
    ADD COLUMN last_reason_code VARCHAR(64) NULL,
    ADD COLUMN last_verified_at DATETIME(6) NULL,
    ADD UNIQUE INDEX uk_source_runtime_ingress_id (ingress_id),
    ALGORITHM=INPLACE,
    LOCK=NONE;

UPDATE media_source_runtime
   SET publisher_mode = CASE
       WHEN source_type = 'FIXED_CAMERA' THEN 'FIXED_CAMERA_GATEWAY'
       ELSE 'DEVICE_CLIENT'
   END
 WHERE publisher_mode IS NULL;

ALTER TABLE media_source_runtime
    MODIFY COLUMN publisher_mode VARCHAR(32) NOT NULL,
    ALGORITHM=INPLACE,
    LOCK=NONE;
```

约束：

- `publisher_mode` 是生命周期分流依据，枚举固定为 `DEVICE_CLIENT`、`FIXED_CAMERA_GATEWAY`、
  `LIVEKIT_INGRESS`。机器人历史 Runtime 回填 `DEVICE_CLIENT`，固定摄像头历史 Runtime 回填
  `FIXED_CAMERA_GATEWAY`。
- `ingress_id` 为 `NULL` 只表示当前没有 Ingress 资源；MySQL 唯一索引允许多行 `NULL`。
- 禁止用 `ingress_id IS NULL/NOT NULL` 推断摄像头应走 Gateway 还是 Ingress 分支。
- `publisher_revision` 是发布模式 generation，只由协议切换和删除收口推进；机器人恒为 `0`。播放门禁
  只比较该字段，普通 Ingress CRUD 不得修改它。
- `ingress_operation_revision` 是 Media 已接纳的最高 Ingress CRUD fencing token；
  `accepted_ingress_operation` 保存该 token 对应的 `CREATE/ROTATE/REVOKE`。二者在 LiveKit 外部调用前
  通过独立短事务提交，不参与播放门禁，也不表示操作已成功完成。
- `last_stream_status`、`last_reason_code` 和 `last_verified_at` 共同保存上次成功核验事实，用于
  LiveKit 短时不可用、Media 重启或现有调度租约接管时保持一致。
- 不新增 `stream_key`、`push_url` 或 RTMP 专用映射表。
- 生产环境先执行迁移，再发布引用字段的新 Media 镜像。
- 由于最终将 `publisher_mode` 收紧为非空，执行本迁移前必须停止所有旧 Media 实例，避免旧代码
  在迁移后写入缺少 `publisher_mode` 的新 Runtime。首期采用维护窗口整体升级，不支持滚动升级。
- 新 Media 与新 Control 是不可拆分版本单元：旧 Control 不提供预期 mode/revision，新 Control 也不能依赖
  旧 Media 的模式门禁。禁止旧 Control + 新 Media 或新 Control + 旧 Media 混跑。
- `application-dev.yml` 的 `ddl-auto=update` 只能用于本地开发，不能替代生产迁移脚本。

### 4.2 Management 数据库

现有 `protocol_type varchar(32)` 可直接保存 `RTMP`；`main_stream_url` 允许 RTMP 模式为 `NULL`。首期
Management 单实例，只增加切换/删除状态和跨服务 fencing revision：

```sql
ALTER TABLE fixed_camera
    MODIFY COLUMN main_stream_url VARCHAR(1024) NULL COMMENT 'RTSP 主码流地址；RTMP 模式为空',
    ADD COLUMN media_transition_state VARCHAR(32) NOT NULL DEFAULT 'STABLE' COMMENT '媒体编排状态',
    ADD COLUMN publisher_revision BIGINT NOT NULL DEFAULT 0 COMMENT '发布模式 generation',
    ADD COLUMN ingress_operation_revision BIGINT NOT NULL DEFAULT 0 COMMENT 'Ingress CRUD fencing revision';
```

`media_transition_state` 只允许 `STABLE`、`SWITCHING_TO_RTMP`、`SWITCHING_TO_RTSP`、`DELETING`。
不增加通用 `version/@Version`，也不依赖当前未注册的 MyBatis-Plus 乐观锁插件；不增加 phase 和持久化
错误字段。

所有媒体变更都在 cameraId keyed lock 内通过明确 SQL 原子递增对应 revision。普通 Ingress 创建、轮换、
撤销只递增 `ingress_operation_revision`，保持 `STABLE`；协议切换和删除递增 `publisher_revision`，涉及
Ingress 时同时递增 `ingress_operation_revision`。不创建当前没有查询场景的 state/updated_at 索引。
条件更新必须检查受影响行数为 1。远程调用期间不持有 Management 数据库事务。服务重启后 keyed lock
可丢失，但 state 和两类 revision 保留；相同切换或删除请求复用当前 revision 完整幂等重试，不匹配操作返回
`FIXED_CAMERA_MEDIA_TRANSITION_IN_PROGRESS`。实际表名以 Management 现有建表脚本为准。

## 5. 接口数据契约

### 5.1 Management 对前端

沿用 Management 的 `ApiResponse<T>` 包装。

```http
POST   /api/v1/management/fixed-cameras/{cameraId}/publish-config
GET    /api/v1/management/fixed-cameras/{cameraId}/publish-config
PUT    /api/v1/management/fixed-cameras/{cameraId}/publish-config
DELETE /api/v1/management/fixed-cameras/{cameraId}/publish-config
```

请求体均为空。路径中的 `cameraId` 使用现有固定摄像头 Long ID 字符串表示。

创建或轮换后，`data` 使用以下结构：

```json
{
  "cameraId": "2092258082746281985",
  "protocolType": "RTMP",
  "configured": true,
  "streamStatus": "OFFLINE",
  "reasonCode": "RTMP_TRACK_MISSING",
  "observedAt": "2026-09-17 10:00:00",
  "mediaTransitionState": "STABLE",
  "publisherRevision": 4,
  "credentialIssued": true,
  "pushUrl": "rtmp://<public-host>:1935/live/RT_xxx"
}
```

GET 或幂等 POST 命中既有配置时：

```json
{
  "cameraId": "2092258082746281985",
  "protocolType": "RTMP",
  "configured": true,
  "streamStatus": "ONLINE",
  "reasonCode": null,
  "observedAt": "2026-09-17 10:05:00",
  "mediaTransitionState": "STABLE",
  "publisherRevision": 4,
  "credentialIssued": false,
  "pushUrl": null
}
```

规则：

- `POST` 是幂等创建。首次创建返回一次凭证；已有配置时不重新生成，只返回脱敏状态。
- 因网络中断丢失首次响应时，客户端不得靠重试 POST 取回旧 Key，应提示管理员执行 PUT 轮换。
- `PUT` 表示轮换，成功后返回一次新凭证。
- `DELETE` 幂等撤销，返回空 `data`；资源本来不存在也视为成功。
- `ingressId` 不对管理端前端暴露，前端没有使用它的业务场景。
- Management 对前端只返回完整 `pushUrl`，不同时暴露重复的 `streamKey` 字段。
- 完整 `pushUrl` 不进入摄像头详情、列表、任务或路径响应。

### 5.2 Media 内部接口

Media 内部接口返回原始 DTO，不套 Management 的 `ApiResponse`：

```http
POST   /internal/media/fixed-camera-ingresses
GET    /internal/media/fixed-camera-ingresses/{cameraId}
PUT    /internal/media/fixed-camera-ingresses/{cameraId}
DELETE /internal/media/fixed-camera-ingresses/{cameraId}
POST   /internal/media/fixed-camera-ingresses/status-query
POST   /internal/media/fixed-camera-sources/{cameraId}/publisher-mode
POST   /internal/media/fixed-camera-sources/{cameraId}/quiesce
GET    /internal/media/fixed-camera-sources/{cameraId}/publisher-presence
```

创建请求：

```json
{
  "cameraId": "2092258082746281985"
}
```

Media 单项响应：

```json
{
  "cameraId": "2092258082746281985",
  "ingressId": "IN_xxx",
  "publisherMode": "LIVEKIT_INGRESS",
  "publisherRevision": 4,
  "ingressOperationRevision": 12,
  "configured": true,
  "roomName": "media.fixed.2092258082746281985.visible.main",
  "participantIdentity": "fixed-camera:2092258082746281985",
  "streamStatus": "OFFLINE",
  "reasonCode": "RTMP_TRACK_MISSING",
  "observedAt": "2026-09-17 10:00:00",
  "credentialIssued": true,
  "url": "rtmp://<public-host>:1935/live",
  "streamKey": "RT_xxx"
}
```

Management 使用 `url + "/" + streamKey` 生成完整 `pushUrl`。拼接时只去除 `url` 末尾重复斜杠，
不得重写协议、主机、端口或 path。

批量状态请求与响应：

```json
{
  "cameraIds": ["2092258082746281985", "2092258082746281986"]
}
```

```json
{
  "items": [
    {
      "cameraId": "2092258082746281985",
      "configured": true,
      "streamStatus": "ONLINE",
      "reasonCode": null,
      "observedAt": "2026-09-17 10:05:00"
    },
    {
      "cameraId": "2092258082746281986",
      "configured": false,
      "streamStatus": "UNKNOWN",
      "reasonCode": "INGRESS_NOT_CONFIGURED",
      "observedAt": null
    }
  ]
}
```

批量接口要求：去重、保持输入顺序、单次最多 500 个 ID；空列表返回空 `items`，不得逐项发起 HTTP 调用。

`publisher-mode` 接口只允许 Control 服务身份调用，请求体为：

```json
{
  "targetMode": "LIVEKIT_INGRESS"
}
```

它负责先持久化目标发布方式，再收口相关 VideoSession、录像和 Runtime Track，并返回需要 Control
下发的 Gateway stop 载荷列表。它不创建 Ingress，也不修改 Management 摄像头档案。

`quiesce` 同样只允许 Control 服务身份调用，用于删除 RTSP 摄像头：它保持
`publisherMode=FIXED_CAMERA_GATEWAY` 不变，关闭活动 VideoSession/录像，返回精确的 Gateway stop 载荷，
并禁止旧 revision 再次签发 Publisher Token。Control 发送 stop 后使用 `publisher-presence` 核验旧
Participant/Track 已退出；它不是普通播放停止接口。

Ingress POST/PUT/DELETE 必须携带：

```http
X-Ingress-Operation-Revision: 12
```

`publisher-mode` 和 `quiesce` 必须携带：

```http
X-Publisher-Revision: 4
```

Media 在 Runtime 悲观锁内分别比较对应 revision，禁止混用。Ingress fencing 按“接纳”而不是
“成功完成”推进：

1. 用独立短事务悲观锁定 Runtime。小于 `ingress_operation_revision` 返回
   `FIXED_CAMERA_MEDIA_OPERATION_STALE`；等于但操作类型不同返回
   `FIXED_CAMERA_MEDIA_OPERATION_CONFLICT`。
2. 大于当前值时，在该事务中保存 `ingress_operation_revision` 和
   `accepted_ingress_operation`，提交后才允许调用 LiveKit。该 token 一经接纳永不回退，
   即使外部调用失败也保留。
3. 接纳事务提交后，另开执行事务再次悲观锁定 Runtime，校验 token/type 仍为已接纳值。
   执行事务在持有该行锁时完成同 cameraId 的 List/Create/Delete。从获得并发许可后开始计时，
   Media Ingress 操作总预算为 5 秒，覆盖接纳事务、执行事务、所有 LiveKit 调用、数据库提交和结果组装，
   以串行化同 token 重试、更高 token 请求和并发请求线程的外部副作用。
   单次 LiveKit HTTP 调用最多 3 秒，实际超时取 `min(3 秒, Media 总预算剩余时间)`；List、Delete、Create
   等多次调用共享同一个 5 秒 deadline，每次调用不得重置总预算。
   Runtime 行锁等待、数据库语句和事务提交也必须受同一 deadline 约束；剩余时间不足时立即终止，
   不得在 LiveKit 超时之外继续无界等待数据库锁。
4. 等于当前值且操作类型相同时，不盲目 no-op；必须通过 Runtime、ListIngress 和
   metadata 对账目标状态，已达成才幂等成功，否则从当前资源事实继续补齐。
5. LiveKit 副作用返回后、执行事务提交前再次校验 token/type；只有仍与已接纳值相等时才能
   回写 `ingress_id`、Track 和会话结果。HTTP 超时或进程崩溃只回滚执行事务，已接纳 token/type 仍保留。
6. 如果 LiveKit 在客户端超时后才完成旧请求，资源 metadata 将其标识为过期结果。能获得明确资源 ID 时
   立即补偿删除；结果不确定或补偿失败时，由统一对账任务继续清理。过期破坏性操作的结果
   不能覆盖新 token 状态，由当前 token 重试或对账恢复目标状态。

为防止不同摄像头同时执行上述长事务时占满共享 Hikari 连接池，Media 必须在打开接纳事务前先获取
进程内全局、可回收的 Ingress 管理并发许可。首期默认最大并发数为 2，许可等待最长 200 ms；
超时返回可重试 `FIXED_CAMERA_INGRESS_BUSY`，不打开数据库事务，也不接纳新 token。许可覆盖
POST/PUT/DELETE 和进入 Runtime 执行事务的补偿/对账变更，必须在 `finally` 中释放；GET、状态查询和
只读 Room 对账不占用许可。

每个 Media 实例必须满足
`Hikari maximumPoolSize >= max(4, ingressAdminMaxConcurrency * 4)`，为机器人、RTSP、会话和录像路径
保留至少 75% 的连接容量。默认 Ingress 并发数为 2 时，Hikari `maximumPoolSize` 至少为 8。启动时对该约束
做配置校验；不满足时拒绝启动，不能只记录警告。

配置操作的接纳或失败均不得推进 `publisher_revision`。

端点模式前置条件：

| 端点 | 允许模式 | 模式不符合时 |
| --- | --- | --- |
| Ingress POST/PUT | `LIVEKIT_INGRESS` | 返回发布模式冲突 |
| Ingress DELETE | `LIVEKIT_INGRESS` | 已为 `FIXED_CAMERA_GATEWAY` 且无 Ingress 时安全 no-op；其他情况冲突 |
| `quiesce` | `FIXED_CAMERA_GATEWAY` | 返回发布模式冲突 |
| `publisher-mode` | 预期源模式或目标模式 | 已为目标模式时不执行清理副作用；更大 revision 只推进 generation |

`publisher-mode` 请求小于当前 `publisher_revision` 时拒绝；大于当前值时领取新的 generation 后执行；
相等且仍为预期源模式时继续未完成切换，相等且已为目标模式时幂等成功。当请求大于当前值且
已为目标模式时，只持久化新的 `publisher_revision`，不清空 Track、不关闭新会话，也不重复清理旧发布端。

`publisher-presence` 通过 Room API 二次核验固定 Participant 和视频 Track 是否存在，只返回
`participantPresent`、`trackPresent`、`observedAt`，不返回 Token 或凭证。Control 在发送 Gateway stop 后调用它
确认旧发布端已退出。

### 5.3 数据库 deadline 与锁超时契约

Ingress 管理不得直接复用机器人和 RTSP 通用的 `findByIdForUpdate` 锁方法。Media 必须新增
Ingress 专用 Runtime 加锁路径，仅 POST/PUT/DELETE 及其补偿、对账变更使用；不改变
机器人、RTSP 会话和 Publisher Token 现有数据库锁等待行为。

5 秒 Media 总预算使用单调时钟生成绝对 deadline，获得全局并发许可后不再重置。实现必须
在开启接纳事务前、每次 Runtime 加锁前、每次 LiveKit 调用前以及执行事务提交前
重新计算剩余预算。预算已用尽时立即终止，不得再发起数据库或 LiveKit 副作用。

每个 Ingress 接纳事务和执行事务均须设置不超过当前剩余预算的 transaction timeout；
Ingress 专用悲观锁查询同时设置 `jakarta.persistence.lock.timeout` 和查询超时。JPA/Hibernate 提示
不能单独作为 MySQL 行锁硬截止的保证，必须用项目目标 MySQL 版本验证实际
`SELECT ... FOR UPDATE` 失败时间。

如果目标 MySQL 不尊重 JPA lock timeout，则在 Ingress 专用事务的当前连接上临时设置
`SESSION innodb_lock_wait_timeout` 前，必须先在同一物理连接上读取并保存原始会话值；
临时值取不超过剩余预算的整秒值。剩余预算不足 1 秒时不再尝试加锁，
直接返回 `FIXED_CAMERA_INGRESS_BUSY`。连接归还 Hikari 前必须在 `finally` 中精确恢复之前
读取的原始值，不得恢复为硬编码默认值或当前全局值。

恢复会话变量失败时，必须将当前事务标记为回滚，并通过 Hikari 显式淘汰当前物理连接；
仅调用 JDBC `close()` 不算淘汰，因为它可能只将代理连接归还连接池。如显式淘汰不可用或
失败，必须 abort/破坏底层连接，确保它不会被复用。该请求返回
`FIXED_CAMERA_INGRESS_UNAVAILABLE`，原始业务异常作为 suppressed cause 保留用于诊断。禁止修改全局
`innodb_lock_wait_timeout`。

异常映射必须遍历 `Throwable.getCause()` 和每个 `SQLException.getNextException()` 构成的完整异常图，
收集所有 `SQLException` 的 SQLState 和 MySQL vendor code，
区分“资源竞争”与“数据库故障”。`LockTimeoutException`、`PessimisticLockException`、
`CannotAcquireLockException`、`QueryTimeoutException` 等只是候选外层异常，不得单独决定业务错误码。
遍历必须使用对象身份集合去重，最多处理 64 个异常节点，防止 cause/nextException 环或过长链无界遍历。
超过上限时记录截断日志并按未识别持久化异常处理，不得根据不完整异常图推测 busy。
收集完成后按以下优先级分类：

| 优先级 | 根因 | 判定 | 业务结果 |
| --- | --- | --- | --- |
| 1 | 数据库连接或通信失败 | SQLState 类 `08` | `FIXED_CAMERA_INGRESS_UNAVAILABLE` |
| 2 | 完整性约束、SQL 语法、字段/迁移不匹配 | SQLState 类 `23` 或对应 SQLState/vendor code | 现有内部错误，不可重试 |
| 3 | MySQL 锁等待超时 | vendor code `1205` | `FIXED_CAMERA_INGRESS_BUSY` |
| 4 | MySQL 死锁/可序列化冲突 | vendor code `1213` 或 SQLState `40001` | `FIXED_CAMERA_INGRESS_BUSY` |
| 5 | Media 本地单调 deadline 已到期 | 由 Ingress 专用 deadline 上下文明确标记 | `FIXED_CAMERA_INGRESS_BUSY` |
| 6 | 未识别的持久化异常 | 无明确 deadline、SQLState 或 vendor code 依据 | 现有内部错误，不得猜测为 busy |

异常图中同时出现多种 SQL 根因时，必须按上表优先级决定唯一业务结果，不使用“最近”或
“最底层”异常猜测。只有由 Ingress 专用定时器明确触发的事务或查询超时，才可在无
`SQLException` 时按 busy 处理。
普通 `QueryTimeoutException` 不自动等于锁等待超时。

Runtime 锁未获取时禁止调用 LiveKit；全局并发许可仍必须在最外层 `finally`
释放。如 token 已在接纳事务中持久化，返回 busy 时保留已接纳状态，同 token/type
重试继续补齐；未接纳则不得推进 token。

事务提交的绝对耗时不能只靠 JPA 作硬保证；实施必须通过事务超时、JDBC 查询/网络超时和
提交前剩余预算检查共同约束，并以真实 MySQL 集成测试记录的最大实际耗时作为验收依据。

### 5.4 错误响应

Media 使用现有错误响应形状：

```json
{
  "status": 409,
  "code": "FIXED_CAMERA_INGRESS_NOT_CONFIGURED",
  "message": "固定摄像头尚未配置 RTMP 推流",
  "retryable": false,
  "requestId": "req_xxx",
  "path": "/internal/media/fixed-camera-ingresses/..."
}
```

| 错误码 | HTTP | retryable | 产生方 |
| --- | --- | --- | --- |
| `INGRESS_DISABLED` | 503 | false | Media |
| `FIXED_CAMERA_NOT_FOUND` | 404 | false | Management |
| `FIXED_CAMERA_PROTOCOL_NOT_RTMP` | 409 | false | Management |
| `FIXED_CAMERA_INGRESS_NOT_CONFIGURED` | 409 | false | Media/Control |
| `FIXED_CAMERA_INGRESS_UNAVAILABLE` | 503 | true | Media |
| `FIXED_CAMERA_INGRESS_BUSY` | 503 | true | Media |
| `FIXED_CAMERA_PUBLISHER_MODE_TRANSITION` | 409 | true | Media/Control |
| `FIXED_CAMERA_MEDIA_TRANSITION_IN_PROGRESS` | 409 | true | Management/Control |
| `FIXED_CAMERA_MEDIA_OPERATION_STALE` | 409 | false | Media |
| `FIXED_CAMERA_MEDIA_OPERATION_CONFLICT` | 409 | false | Media |
| `FIXED_CAMERA_STREAM_OFFLINE` | 409 | true | Control |
| `FIXED_CAMERA_STREAM_STATUS_UNKNOWN` | 503 | true | Control |

Management 调用 Media 时保留下游业务码，不统一改写成笼统的 `BUSINESS_ERROR`。

所有 REST 时间字段沿用 Media/Control 现有 `DateTimeConfig`，以上海时区 `yyyy-MM-dd HH:mm:ss`
输出；本文示例不使用带时区的 ISO-8601 形式。

## 6. Media Service 实现

### 6.1 代码落点

| 位置 | 改动 |
| --- | --- |
| `video/model/VideoSourceRuntime.java` | 增加 `publisherMode`、`publisherRevision`、`ingressOperationRevision`、`acceptedIngressOperation`、`ingressId` 和最后流状态字段 |
| `media-common/.../VideoPublisherMode.java` | 新增共享枚举 `DEVICE_CLIENT/FIXED_CAMERA_GATEWAY/LIVEKIT_INGRESS` |
| `media-common/.../CreateVideoSessionRequest.java` | 增加固定摄像头必填的 `expectedPublisherMode`、`expectedPublisherRevision` |
| `media-common/.../VideoSessionResponse.java` | 增加只用于观测的 `publisherMode`、`publisherRevision` |
| `video/repository/VideoSourceRuntimeRepository.java` | 增加按 `sourceType/sourceId`、`publisherMode`、`ingressId` 和对账集合查询；保留现有通用锁方法语义 |
| `video/repository/FixedCameraIngressRuntimeLockRepository.java` | 新增 Ingress 专用 Runtime 加锁、动态剩余预算、MySQL 锁等待控制和恢复失败连接淘汰 |
| `video/service/FixedCameraIngressPersistenceExceptionClassifier.java` | 遍历 cause/nextException 异常图，按本地 deadline、SQLState 和 MySQL vendor code 优先级分类 |
| `livekit/LiveKitTokenService.java` | 增加只含 `ingressAdmin=true` 的 Ingress 管理 Token |
| `livekit/LiveKitIngressService.java` | 新增 LiveKit Ingress Twirp Client |
| `video/service/FixedCameraIngressService.java` | 新增配置生命周期、全局并发许可、状态派生和补偿清理 |
| `video/api/FixedCameraIngressController.java` | 新增内部 CRUD 和批量状态接口 |
| `video/scheduler/VideoSessionTimeoutScheduler.java` | 扩展现有 5 秒 LiveKit 对账，不新建第二个调度器 |
| `video/service/VideoSessionService.java` | 复用 Runtime Track、隔离 RTMP 的停止和恢复行为 |
| `video/api/ApiExceptionHandler.java` | 输出本方案业务错误码 |
| `config/MediaProperties.java` | 增加 Ingress 开关和状态陈旧阈值 |

DTO 可放在 `media-common` 供 Control 和 Media 共享；Management 侧仍在自身 `MediaServicePort` 中定义
对应 port record，不让 Management 依赖 `robot-mediaserver` 的 Java 模块。

### 6.2 LiveKit API

Media 调用 LiveKit Server 的 Twirp API，Ingress Worker 不是 HTTP 管理入口：

```text
POST /twirp/livekit.Ingress/CreateIngress
POST /twirp/livekit.Ingress/ListIngress
POST /twirp/livekit.Ingress/DeleteIngress
```

首期轮换采用删除旧 Ingress 后创建新 Ingress，不使用 `UpdateIngress` 修改 Key。官方协议限定
`UpdateIngress` 只能在 `ENDPOINT_WAITING` 状态使用，也不能替代凭证轮换语义。

创建请求：

```json
{
  "inputType": "RTMP_INPUT",
  "name": "fixed-camera-2092258082746281985",
  "roomName": "media.fixed.2092258082746281985.visible.main",
  "participantIdentity": "fixed-camera:2092258082746281985",
  "participantMetadata": "{\"managedBy\":\"robot-mediaserver\",\"sourceType\":\"FIXED_CAMERA\",\"cameraId\":\"2092258082746281985\",\"operationRevision\":12,\"operationType\":\"CREATE\",\"createdAtEpochSeconds\":1789603200,\"schemaVersion\":1}",
  "enableTranscoding": true,
  "video": {
    "name": "camera",
    "source": "CAMERA",
    "preset": "H264_1080P_30FPS_3_LAYERS"
  }
}
```

请求使用上述 camelCase Twirp JSON 字段，并通过锁定版本的集成测试确认。不得发送 `audio`，不得继续使用
已废弃的 `bypassTranscoding`。

现有 `createAdminToken()` 只有 Room/Egress 权限。必须新增专用方法并包含：

```json
{
  "video": {
    "ingressAdmin": true
  }
}
```

不要把 `ingressAdmin` 加到 Viewer、Publisher 或普通 Room Token。

### 6.3 Runtime 初始化

创建 Ingress 前，使用现有确定性 Runtime 规则创建或锁定：

```text
sourceType = FIXED_CAMERA
sourceId  = cameraId
deviceId  = cameraId
channel   = visible
quality   = main
roomName  = media.fixed.{cameraId}.visible.main
```

这样 Ingress 在 API 返回后立即推流、Webhook 很快到达时，Media 已能通过 Room 找到 Runtime。

创建前必须把 `publisher_mode` 持久化为 `LIVEKIT_INGRESS`。它与 Ingress 资源的创建、删除、
轮换成功与否解耦，只能在明确的协议切换编排中改为 `FIXED_CAMERA_GATEWAY`。

所有 Runtime 创建入口必须显式赋值：机器人为 `DEVICE_CLIENT`；首次播放且尚无 Runtime 的 RTSP
固定摄像头为 `FIXED_CAMERA_GATEWAY`；RTMP 只能由推流配置或协议切换流程创建为 `LIVEKIT_INGRESS`。
不给数据库列设置会隐藏代码遗漏的默认值。

### 6.4 幂等创建

`POST /internal/media/fixed-camera-ingresses`：

1. 校验 `LIVEKIT_INGRESS_ENABLED=true`、`cameraId` 和 `X-Ingress-Operation-Revision`。
2. 在独立短事务中创建或悲观锁定 Runtime；Runtime 不存在时初始化为
   `publisher_mode=LIVEKIT_INGRESS`。按第 5.2 节校验并提交最高 token 及
   `accepted_ingress_operation=CREATE`，提交后释放数据库锁。
3. 另开执行事务悲观锁定 Runtime，再次校验 token/type，在行锁保护下调用 ListIngress，
   按 `ingress_id`、确定性名称、Room、Identity 和平台归属 metadata 对账。
4. Runtime `ingress_id` 已映射且 LiveKit 仍存在该唯一平台资源时，将其作为 CREATE 的当前权威配置，
   即使 metadata token 更早也不删除；返回 `credentialIssued=false`，不得返回旧 Key。数据库无权威映射但
   存在唯一当前 token/type 资源时，恢复映射后同样返回 `credentialIssued=false`。POST 不隐式轮换，
   管理员需使用 PUT 获得新凭证。
5. 数据库有 ID、LiveKit 无资源时清空旧 ID，但保留 `publisher_mode=LIVEKIT_INGRESS` 和已接纳 token/type。
6. 匹配多个资源时先删除过期 token 资源并收敛为一个当前 token/type 资源；任一删除失败即返回
   可重试 503，不再创建。
7. 仍需创建时，用 `managedBy/cameraId/operationRevision/operationType/createdAtEpochSeconds` metadata
   调用 CreateIngress。
8. CreateIngress 成功后、执行事务提交前再次校验 token/type，保存 `ingress_id` 和归一状态。
   如果调用返回时已无法确认 token/type，不回写并立即补偿删除本次创建的 Ingress。
9. 最终回写失败或补偿删除失败时，由统一对账任务根据 metadata 继续收敛；已接纳 token 不回退。

接纳事务不得跨 LiveKit 调用；只有第 5.2 节定义的执行事务可在 Media 5 秒总预算内持有 Runtime 行锁
调用 LiveKit，不得在同一事务中处理其他 cameraId。单次 LiveKit HTTP 调用最多 3 秒，且不得超过总预算
剩余时间。CreateIngress 超时或连接中断属于“结果不确定”：
当前请求返回可重试 503，相同 token/type 重试必须先
ListIngress 对账；新的更高 token 请求会取代旧操作，过期结果由补偿和对账清理。首期不增加
provisioning 状态字段或 Outbox。

### 6.5 查询

`GET` 与批量状态查询均不得返回可使用的旧 Key。即使 LiveKit ListIngress 返回 `stream_key`，Media 也必须
在 DTO 映射前清空。只有当前请求刚成功 CreateIngress 时才可把该响应中的 `url` 和 `streamKey` 向上返回。

### 6.6 轮换

`PUT` 要求 `LIVEKIT_INGRESS_ENABLED=true`，并使用明确的两阶段行为：

1. 用独立短事务接纳 token 和 `accepted_ingress_operation=ROTATE`，提交后释放数据库锁。
2. ListIngress 对账；已存在唯一当前 token/type 的新 Ingress 时恢复映射并返回
   `credentialIssued=false`，不再轮换也不恢复旧 Key。
3. 未达到目标状态时，删除当前 token 之前的平台 Ingress；删除失败且不是 404 时返回 503。
4. 删除成功后再次校验 token/type；仍为当前操作时清空旧 `ingress_id`、Track 和核验状态，
   将关联活动会话置为 `INTERRUPTED`。`publisher_mode` 全程保持 `LIVEKIT_INGRESS`。
5. 按第 6.4 节使用当前 token/type 创建新 Ingress，并在回写前再次校验 token/type。
6. 只有本次刚创建资源且未被更高 token 取代时，才返回一次新 `url` 和 `streamKey`。

若第 4 步失败，摄像头处于“未配置”而不是回滚成已失效的旧 Ingress。管理员可再次创建。首期允许轮换
期间断流，不为无缝轮换临时保留两个长期有效的 Ingress。

### 6.7 撤销

`DELETE`：

1. 不受 `LIVEKIT_INGRESS_ENABLED=false` 限制；关闭新能力后仍必须允许清理。
2. 用独立短事务校验模式并接纳 token 和 `accepted_ingress_operation=REVOKE`。模式已为
   `FIXED_CAMERA_GATEWAY` 且没有平台 Ingress 时安全成功，禁止清理 Gateway Track 或会话；其他模式错误返回冲突。
3. ListIngress 对账当前 cameraId 的所有平台 Ingress。仍有任一资源时继续幂等删除；已无资源时
   直接进入运行态收口。
4. LiveKit 删除成功或返回 404 后，再次锁定 Runtime 校验 token/type。仍为当前操作时，
   清空 `ingress_id`、Participant/Track 和核验时间，结束活动 RTMP VideoSession 并停止相关录像。
5. 删除失败时保留已接纳 token/type 和现有映射，返回 503，相同 token/type 重试必须继续删除，
   禁止伪报撤销成功。
6. 可删除空 Room，但不得删除 `media_source_runtime` 行，避免并发 Webhook 和会话引用失去落点。

### 6.8 状态派生

平台状态只使用以下枚举：`ONLINE`、`OFFLINE`、`UNKNOWN`。

| 条件 | 状态 | reasonCode |
| --- | --- | --- |
| `publisher_mode=LIVEKIT_INGRESS` 且 `ingress_id` 为空 | `UNKNOWN` | `INGRESS_NOT_CONFIGURED` |
| LiveKit 查询成功，预期 Participant 有视频 Track | `ONLINE` | `null` |
| Ingress 为 `ENDPOINT_BUFFERING` 且无 Track | `OFFLINE` | `RTMP_BUFFERING` |
| Ingress 为 `ENDPOINT_ERROR` | `OFFLINE` | `RTMP_INGRESS_ERROR` |
| Ingress 存在但预期 Track 不存在 | `OFFLINE` | `RTMP_TRACK_MISSING` |
| LiveKit 查询失败且上次成功核验未超过 15 秒 | 保留上次状态 | 保留上次原因 |
| LiveKit 查询失败且超过 15 秒 | `UNKNOWN` | `LIVEKIT_STATUS_STALE` |

`ONLINE` 的权威条件是 Room API 中存在固定 Participant 的有效视频 Track。Ingress state 可用于原因诊断，
但不能在 Track 尚未发布时单独把摄像头标成在线。每次成功完成 LiveKit 核验后同时更新
`last_stream_status`、`last_reason_code` 和 `last_verified_at`。查询失败的 15 秒容忍期内从这些持久化字段
返回上次结果，不使用单机内存缓存。

### 6.9 Webhook 与周期对账

- 继续使用现有 Webhook 验签和 Room API 二次核验，不直接相信事件正文中的 Track 状态。
- `participant_joined`、`participant_left`、`track_published`、`track_unpublished`、`room_finished` 均触发 Room 对账。
- 扩展现有 `VideoSessionTimeoutScheduler.reconcileLiveKitTracks()`，对账集合为当前活动会话 Runtime
  `UNION publisher_mode=LIVEKIT_INGRESS` Runtime，去重后统一处理。
- 对账继续使用现有 `media-video-session-maintenance` 数据库短租约，禁止新建独立 5 秒调度器。
- 这是必要差异：RTMP 摄像头在无人观看时也持续推流，必须提前形成装备在线状态。
- 同一任务周期 ListIngress，对带 `managedBy=robot-mediaserver` 的平台资源进行漂移收敛。
  metadata 必须包含 `cameraId`、`operationRevision`、`operationType` 和 `createdAtEpochSeconds`。
- 资源 `operationRevision` 小于 Runtime 已接纳 token，且不是
  `accepted_ingress_operation=CREATE` 时 Runtime 当前权威 `ingress_id` 的资源，则属于过期外部副作用，
  立即删除；不重新映射，也不等待 60 秒保护期。
- 存在与 Runtime 已接纳 token/type 精确匹配的唯一资源但 `ingress_id` 未映射时，恢复映射与状态，
  但不返回 Stream Key。
- `REVOKE` 为当前已接纳操作时，删除该 cameraId 下所有平台 Ingress，直到目标状态为空。
- `ingress_id` 未被任何 Runtime 引用、又无法与当前 token/type 对账，且
  `createdAtEpochSeconds` 超过 60 秒保护期的资源视为普通孤儿并删除；删除失败下周期重试。
- 对账发现 Track 时更新 Runtime，并把同 Runtime 的 `INIT/ROOM_READY/INTERRUPTED` 会话收敛为 `STREAMING`。
- 对账发现 Track 丢失时清空 Track，并把 `STREAMING` 会话置为 `INTERRUPTED`。
- Track 恢复后复用原 VideoSession 和 Viewer，不向 Control 生成 start/restart command。

### 6.10 VideoSession 必须修改的分支

新建或复用固定摄像头会话时，Control 必须传入 `expectedPublisherMode`、`expectedPublisherRevision`。
Media 在 `create()` 已持有 Runtime 悲观锁的同一事务内完成：

1. 校验 `expectedPublisherRevision == runtime.publisherRevision`。
2. 校验 `expectedPublisherMode == runtime.publisherMode`。
3. 创建或复用 VideoSession，并从 Runtime 复制有效 Track。
4. 组装响应中的 `publisherMode/publisherRevision` 观测字段。

任一校验失败都返回可重试冲突，不创建会话。响应字段不是 Control 发 MQTT 的唯一安全门禁。

`publisher_mode=LIVEKIT_INGRESS` 时：

- 从 Runtime 复制 `trackSid`、`trackName`。
- Runtime Track 经 Room API 确认有效时，直接设置 `STREAMING`。
- 不生成 Publisher Token，不执行 `requestClientStart()`。
- `quality` 始终为 `main`，忽略 RTMP 请求中的 `sub`；首期前端本来也不展示切换入口。

空闲释放时：

- Runtime `publisher_mode=LIVEKIT_INGRESS` 表示 RTMP 常驻源，只关闭 VideoSession/Viewer；
  即使 `ingress_id` 为空也不改走 Gateway 逻辑。
- 不删除 Room，不返回 Gateway stop payload，不停止摄像头推流。
- 只有 Runtime `publisher_mode=FIXED_CAMERA_GATEWAY` 才可沿用当前删除 Room 和 Gateway stop 行为。

恢复与重启时：

- `interruptedRestartCandidates`、`fixedCameraRecoveryCommands` 必须排除
  `publisher_mode=LIVEKIT_INGRESS` 的 Runtime，不得按 `ingress_id` 过滤。
- RTMP 手动“重启源”不得下发 MQTT；首期 UI 对 RTMP 中断只提供刷新播放。
- Track 恢复由 Webhook/对账自动完成。

`requestClientStart` 是第二道且最终的安全门禁：

- 所有公开、自动恢复、手动重启和切码流入口必须收敛到同一个按 `runtime -> session` 加锁的实现；
  不得仅调用 `requireSessionForUpdate()` 后签发 Token。
- `sourceType=ROBOT_CAMERA` 只允许 `publisher_mode=DEVICE_CLIENT`。
- `sourceType=FIXED_CAMERA` 只允许 `publisher_mode=FIXED_CAMERA_GATEWAY`。
- `publisher_mode=LIVEKIT_INGRESS` 始终拒绝签发 Publisher Token、创建 `VideoStartCommand` 和进入启动中状态。
- Runtime 锁内读到的 mode/revision 必须与会话创建时的预期值一致；不一致时返回冲突，由 Control
  重读 Management 后重试整个播放流程。

## 7. Management Service 实现

### 7.1 固定摄像头模型与校验

修改：

- `FixedCameraProtocolType` 增加 `RTMP`。
- `FixedCameraEntity` 只增加第 4.2 节的 `mediaTransitionState/publisherRevision/ingressOperationRevision`，不修改 `BaseEntity`，
  不增加 `@Version`。
- `FixedCameraRepository`/Mapper 增加两类 revision 的原子递增、按
  `cameraId + state + publisherRevision` 完成切换和删除的条件更新，返回受影响行数；应用服务必须检查结果为 1。
- `SaveFixedCameraRequest`、`FixedCameraResponse`、`TaskFixedCameraResponse`、
  `InternalFixedCameraMediaSourceResponse` 的 OpenAPI `allowableValues` 统一改为 `RTSP, RTMP`。
- `InternalFixedCameraMediaSourceResponse` 增加 `mediaTransitionState`、`publisherRevision`；只有
  `state=STABLE` 的记录允许发起新播放。
- `FixedCameraResponse` 和推流配置响应增加状态和 `publisherRevision` 字段，供编辑页识别未完成操作；列表可以
  透传但不新增操作按钮。
- `mainStreamUrl` 从 DTO 的无条件 `@NotBlank` 改为应用服务中的条件校验。
- 创建和更新时保存请求中的枚举值，不再强制 `FixedCameraProtocolType.RTSP`。
- 同步修改 `ManagementServiceApplicationTests` 中的 OpenAPI 枚举断言，并补齐路径、任务和内部媒体源响应测试。
- 同步更新 EIOP 根仓库 `docs/contracts.md`、`docs/design.md`、
  `bootstrap/mysql/01-eiop-management-schema.sql` 和 `bootstrap/mysql/upgrade/` 中的幂等升级脚本。

条件校验：

| 协议 | `mainStreamUrl` | `subStreamUrl` | `username/password` |
| --- | --- | --- | --- |
| RTSP | 必填 | 可选 | 可选 |
| RTMP | 必须为空 | 必须为空 | 必须为空 |

RTMP 的 `pushUrl` 是设备侧配置结果，不写回固定摄像头档案。

### 7.2 复用 MediaServicePort

在现有 `MediaServicePort` 增加：

```java
PublishConfig createFixedCameraIngress(String cameraId, long ingressOperationRevision);
PublishConfig getFixedCameraIngress(String cameraId);
PublishConfig rotateFixedCameraIngress(String cameraId, long ingressOperationRevision);
boolean deleteFixedCameraIngress(String cameraId, long ingressOperationRevision);
```

`DirectMediaServiceClient` 增加相应 HTTP 调用和配置路径。`UnavailableMediaServiceClient` 必须实现同一接口，
并明确抛出“Media 服务未启用”，不能返回伪成功。

`DirectMediaServiceClient` 对上述 Ingress POST/PUT/DELETE 使用专用 8 秒超时，不改变其他 Media 端点的
现有 5 秒超时。同一次 Management 业务请求内如需重试，必须复用相同
`X-Ingress-Operation-Revision` 和 HTTP 方法；不得在上层超时后使用缺少 fencing token 的通用重试器。

继续使用配置身份：

```text
X-User-Id = manager-service
X-Roles   = MEDIA_OPERATOR
X-Org-Id  = 仅在 EIOP_MEDIA_SERVICE_ORG_ID 非空时发送
```

不透传浏览器提供的服务身份 Header。

协议切换另新增固定摄像头专用的 `FixedCameraPublisherControlPort` 及 Control HTTP 实现，不把 MQTT
编排塞入 `MediaServicePort`。该 Port 暴露幂等的
`switchPublisherMode(cameraId, targetMode, publisherRevision)` 和
`quiesceForDelete(cameraId, publisherRevision)`。

### 7.3 Management 应用服务

新增 `FixedCameraPublishConfigApplicationService`，职责为：

1. 按现有组织和对象数据权限查找摄像头。
2. 校验 `protocolType=RTMP`。
3. 按第 7.5 节在 keyed lock 内原子获取当前操作对应的 revision，并传给下游。
4. 调用 `MediaServicePort`。
5. 对首次创建或轮换响应组装完整 `pushUrl`。
6. 对 GET 清空凭证字段。

不要把该逻辑塞入地图、路径或任务应用服务。

### 7.4 权限

不新增权限码，继续使用固定摄像头现有权限：

| 操作 | 既有权限 |
| --- | --- |
| GET publish-config | `resource.fixed-camera.view` |
| POST publish-config | `resource.fixed-camera.create` 或 `resource.fixed-camera.edit` |
| PUT publish-config | `resource.fixed-camera.edit` |
| DELETE publish-config | `resource.fixed-camera.edit` |

必须在 `PermissionCatalog` 中把以下四条规则放在现有固定摄像头通配规则之前：

```text
GET    /api/v1/management/fixed-cameras/*/publish-config -> anyOf(view)
POST   /api/v1/management/fixed-cameras/*/publish-config -> anyOf(create, edit)
PUT    /api/v1/management/fixed-cameras/*/publish-config -> anyOf(edit)
DELETE /api/v1/management/fixed-cameras/*/publish-config -> anyOf(edit)
```

现有 `requiredPermission(method, path)` 只能表达单权限。访问控制契约应扩展为
`PermissionRequirement(anyOfPermissions)`，原有单权限规则自动包装为单元素集合，鉴权过滤器判定“至少拥有一个”。
不得通过放宽通配规则、把 DELETE 错配为删除摄像头权限，或新增 `resource.fixed-camera.publish` 规避该改造。
四种方法都继续执行路径 `cameraId` 的对象级数据权限校验；未命中规则必须保持拒绝。

### 7.5 单实例串行与双 revision

首期只支持一个 Management 实例。同一 `cameraId` 的所有普通编辑、启用/禁用、删除、推流配置和协议
切换使用可回收的 keyed lock 串行化；实现可采用固定数量 striped lock，或带上限和过期回收的 keyed-lock
缓存，禁止使用永不清理的 `ConcurrentHashMap<cameraId, Lock>`。

锁内规则：

- 普通字段编辑和启用/禁用只允许 `state=STABLE`；启用/禁用仍只改 `enabled`，不调用 Media。
- POST/PUT/DELETE publish-config 要求 `STABLE`，只原子递增 `ingress_operation_revision`，state 和
  `publisher_revision` 保持不变，再调用 Media。同一次 Management 请求内的下游 HTTP 重试必须复用
  同一 ingress revision 和操作类型；用户在该请求失败后重新提交可领取更高 revision，新 token 取代旧操作。
  迟到低 token 请求由 Media 拒绝，正常播放不受配置调用是否成功影响。
- 双向协议切换从 `STABLE` 条件更新为 `SWITCHING_TO_RTMP/SWITCHING_TO_RTSP`，同时递增
  `publisher_revision` 和 `ingress_operation_revision`。
- 删除摄像头在关系校验通过后从 `STABLE` 更新为 `DELETING` 并递增 `publisher_revision`；RTMP 删除还要
  递增 `ingress_operation_revision`。
- 已处于相同切换或删除 state 的相同请求复用当前两类 revision，从头执行完整幂等流程；不兼容请求返回
  `FIXED_CAMERA_MEDIA_TRANSITION_IN_PROGRESS`。
- `SWITCHING_TO_RTMP` 和 `SWITCHING_TO_RTSP` 本身就是持久化目标方向。服务重启或页面刷新后，恢复请求必须
  分别携带 RTMP、RTSP 目标协议；不得依据档案中尚未更新的当前 `protocolType` 反推恢复方向。切回 RTSP
  且目标表单尚未持久化时，允许管理员重新填写 RTSP URL 和认证信息后重试原 PUT。
- 全部步骤成功后按 `cameraId + state + publisherRevision` 条件更新回 `STABLE` 或删除档案；受影响行数不是 1
  视为冲突，不能覆盖更新。

Management 不主动扫描失败状态。进程崩溃后由用户重新提交相同操作触发恢复。生成配置在 Media 成功但
响应丢失时，重试 POST 只能得到 `credentialIssued=false`；管理员必须执行 PUT 轮换获得新地址，不能恢复
旧 Key。

### 7.6 协议切换与删除

RTSP 改 RTMP：

1. 领取 `SWITCHING_TO_RTMP`、publisher revision 和 ingress operation revision。
2. 携带 publisher revision 调用 Control 切换为 `LIVEKIT_INGRESS`，停止旧 Gateway Publisher 并确认 Track 退出。
3. 保存 RTMP 档案。
4. 携带 ingress operation revision 幂等创建 Ingress，成功后按 state/publisher revision 更新回 `STABLE`。
   首次成功发放凭证时，固定摄像头更新响应复用 `FixedCameraPublishConfigResponse` 返回一次性完整
   `pushUrl`；普通编辑和 RTMP -> RTSP 返回空 data。

RTMP 改 RTSP：

1. 领取 `SWITCHING_TO_RTSP` 和两类 revision。
2. 携带 ingress operation revision 幂等删除 Ingress、关闭会话并确认 Track 退出。
3. 保存已校验的 RTSP 档案。
4. 携带 publisher revision 调用 Control 切换为 `FIXED_CAMERA_GATEWAY`，成功后按 state/publisher revision
   更新回 `STABLE`；下一次正常播放
   才允许 Gateway start。

删除摄像头：

1. 领取 `DELETING` 和 publisher revision；RTMP 同时领取 ingress operation revision。路径不再与固定摄像头
   建立关系，因此不执行已废弃的路径关联校验。此后所有新播放和普通编辑返回冲突。
2. RTMP 先携带 publisher revision 调用 Control，将已是 `LIVEKIT_INGRESS` 的 Runtime 推进到新 generation
   而不清 Track；再携带 ingress operation revision 幂等删除 Ingress、关闭活动会话并确认 Track 退出。
3. RTSP 携带 publisher revision 调用 Control `quiesceForDelete`；Control 经 Media 关闭活动会话和录像、发送精确 Gateway stop，
   并通过 `publisher-presence` 确认 Participant/Track 退出。
4. 按 cameraId/state/publisher revision 条件删除 Management 档案，再发布现有资源变化事件。

任一步失败都保留当前 state 和两类 revision，档案不删除，新发布端不得启动。用户重提相同请求时从本节流程
起点完整幂等重试，不做跨服务分布式事务，也不尝试回滚已撤销的凭证。RTSP 删除完成不依赖 Gateway
目录租约 TTL 自然到期。

### 7.7 失败恢复入口

- Management 查询和编辑响应返回 `mediaTransitionState`；非 `STABLE` 时前端显示“媒体切换未完成，可重试”。
- 前端使用原目标协议和表单数据重新提交相同 PUT；删除由原调用方重新提交相同 DELETE。
- 编辑页重新加载时根据 `SWITCHING_TO_RTMP/SWITCHING_TO_RTSP` 还原目标协议，而不是显示数据库中可能尚未
  更新的当前协议。协议选择保持锁定；`SWITCHING_TO_RTSP` 时开放 RTSP URL 和认证字段用于补录后重试。
- 普通编辑、启用/禁用、反向切换和播放在恢复完成前继续拒绝。
- 不提供面向普通用户的“强制改为 STABLE”接口，也不自动恢复明文 Stream Key。
- 永久配置错误或长期失败由运维先核对 Management state、两类 revision、Media Runtime、Ingress、Participant 和
  Track，再通过受控运维命令重试原流程或修复为与 Media 事实一致的状态；操作过程必须留审计日志。

## 8. Management 前端实现

### 8.1 页面字段

在 `FixedCamerasPage.vue`：

- `protocolOptions` 增加 RTMP。
- RTSP 时显示现有码流 URL 和认证字段。
- RTMP 时隐藏并在提交前清空 `mainStreamUrl/subStreamUrl/username/password`。
- 查看页保持现状；RTMP 的 URL 字段为空时显示 `-`。
- 不新增独立详情页，不新增列表删除按钮。
- 在 `src/eiop/api/domains.js` 的 `fixedCameraApi` 增加 publish-config 的 POST/GET/PUT/DELETE 方法，
  不新建第二套请求封装。

### 8.2 一次保存流程

复用 `CrudPage.afterSave`：

```text
用户点击保存
  -> fixedCameraApi.create(payload)
  -> 获得 cameraId
  -> protocolType == RTMP 时 fixedCameraApi.createPublishConfig(cameraId)
  -> 成功：弹窗只显示一次完整 pushUrl，并提供复制按钮
  -> 失败：保留摄像头，跳转编辑页并提示“档案已保存，推流地址生成失败，可重试”
```

`afterSave` 必须在内部捕获推流配置创建异常并显示上述提示，不能继续向 `CrudPage.saveRow` 抛出异常；否则页面会
停留在新增态，用户再次点击保存将重复创建摄像头档案。

编辑已有 RTSP 摄像头并切换为 RTMP 时，后端在协议切换流程内创建 Ingress；`fixedCameraApi.update` 成功响应
若携带 `credentialIssued=true` 和 `pushUrl`，`afterSave` 必须立即复用同一弹窗展示，不能再调用 PUT 轮换。
若幂等重试返回 `credentialIssued=false`，前端只加载脱敏状态并提示管理员使用“重新生成”取得新地址。

完整地址不得写入 `localStorage`、`sessionStorage`、路由 query、页面全局 store 或前端日志。弹窗关闭后不再从
GET 恢复明文。

### 8.3 编辑页推流配置区

使用现有 `form-extra` 插槽，仅在 RTMP 编辑模式显示：

- 未配置：显示“生成推流地址”。
- 已配置：显示配置状态、流状态、最近核验时间、“重新生成”和“撤销”。
- GET 不返回地址时明确显示“推流地址仅在生成时展示；遗失请重新生成”。
- 轮换和撤销都需要确认对话框，提示可能中断当前视频。
- 不新增 HTTP 调试、试播、模拟推流或连通性测试按钮。
- `mediaTransitionState != STABLE` 时显示“媒体切换未完成，可重试”，保留原目标协议和表单值；只开放
  “重试原操作”和返回列表，不开放普通保存、反向切换或强制清状态。

## 9. Control Service 实现

### 9.1 协议切换编排

Management 通过受信服务身份调用：

```http
POST /internal/control/fixed-cameras/{cameraId}/publisher-mode-switch
```

请求体为：

```json
{
  "targetMode": "LIVEKIT_INGRESS",
  "publisherRevision": 4
}
```

`targetMode` 只允许 `LIVEKIT_INGRESS` 或 `FIXED_CAMERA_GATEWAY`。Control 不修改 Management 档案，
职责是：

1. 携带 `X-Publisher-Revision` 调用 Media `publisher-mode` 接口，以目标模式和 publisher revision 为准幂等
   收口运行态。
2. 切到 `LIVEKIT_INGRESS` 时，对 Media 返回的所有活动 RTSP 会话发送 Gateway stop。
3. 调用 Media `publisher-presence` 轮询 Room API，确认原 `fixed-camera:{cameraId}` Participant/Track 退出；
   超时返回可重试失败，不创建 Ingress。
4. 切到 `FIXED_CAMERA_GATEWAY` 时不主动启动推流，等待下一次正常播放请求触发 Gateway start。

该接口必须是幂等的。同一 `cameraId + targetMode + publisherRevision` 重复请求不重复停止已结束会话，
也不生成新的 Publisher Token；更旧 revision 必须被 Media 拒绝。

删除 RTSP 摄像头使用独立内部接口：

```http
POST /internal/control/fixed-cameras/{cameraId}/publisher-quiesce
```

请求体只包含 `publisherRevision` 和固定原因 `DELETE`。Control 调用 Media `quiesce`、发送返回的精确 stop，
再轮询 `publisher-presence`；确认 Participant/Track 退出后才返回成功。该接口不改变 Management 档案和
Runtime 的目标发布模式。

### 9.2 播放分流

`ControlVideoCommandService.startFixedCameraVideo` 先读取 Management 摄像头。只有
`mediaTransitionState=STABLE` 才继续，并将协议对应的 `expectedPublisherMode` 和
`publisherRevision` 传给 Media。RTSP 摄像头首次播放且 Runtime 不存在时，Media 以该 revision 创建
`FIXED_CAMERA_GATEWAY` Runtime；RTMP 不得在播放请求中隐式创建 Ingress Runtime。

Media 在 Runtime 锁内对 mode/revision 完成原子校验并创建会话。RTSP 随后的 `requestClientStart` 必须再次
在 Runtime 锁内校验相同 mode/revision，成功后才返回 Publisher Token 和 MQTT command；Control 只能发送
该调用返回的 command。`VideoSessionResponse.publisherMode/publisherRevision` 仅用于日志和诊断，不能作为
发送 MQTT 的安全依据。任一阶段冲突时，Control 丢弃旧结果、重新读取 Management 并从播放入口整体重试。

分流矩阵：

```text
enabled=false -> 拒绝
state=STABLE && protocolType=RTSP && publisherMode=FIXED_CAMERA_GATEWAY && publisherRevision 相等 -> 保持现有 RTSP URL + Gateway MQTT 流程
state=STABLE && protocolType=RTMP && publisherMode=LIVEKIT_INGRESS && publisherRevision 相等 -> 固定 quality=main，查询 Media 状态后创建/复用会话
协议与 publisherMode 不一致 -> FIXED_CAMERA_PUBLISHER_MODE_TRANSITION，不发 MQTT，不生成 Publisher Token
state 非 STABLE 或 publisherRevision 不一致 -> FIXED_CAMERA_MEDIA_TRANSITION_IN_PROGRESS，不发 MQTT，不生成 Publisher Token
其他协议 -> 拒绝
```

RTMP 分支：

- 未配置返回 `FIXED_CAMERA_INGRESS_NOT_CONFIGURED`。
- `OFFLINE` 返回 `FIXED_CAMERA_STREAM_OFFLINE`。
- `UNKNOWN` 返回 `FIXED_CAMERA_STREAM_STATUS_UNKNOWN`。
- `ONLINE` 才调用现有 `createVideoSession`。
- 创建后直接返回会话，不调用 `requestClientStart`、`withFixedCameraRtsp` 或 `sendStart`。

批量启动继续逐项复用单路入口并返回现有 `sessions/failures` 结构。

### 9.3 健康状态聚合

`GET /api/control/fixed-cameras/health`：

1. 从 Management 获得当前用户有权摄像头。
2. RTSP 继续使用 `FixedCameraHealthService` 的 Gateway/RTSP 状态。
3. RTMP cameraId 每批最多 500 个调用 Media status-query，分批结果按 Management 原输入顺序合并。
4. 合并为同一 `records[]`，每项增加 `protocolType`。

单批 Media 调用失败时，只将该批 RTMP 摄像头归一为 `UNKNOWN/LIVEKIT_STATUS_STALE`；已成功批次和
RTSP 摄像头状态保留。禁止因一批失败使整个装备列表失败。

RTMP 记录示例：

```json
{
  "cameraId": "2092258082746281985",
  "protocolType": "RTMP",
  "gatewayId": null,
  "gatewayHealth": {
    "status": "UNKNOWN",
    "observedAt": null,
    "reasonCode": "NOT_APPLICABLE"
  },
  "streamHealth": {
    "status": "AVAILABLE",
    "observedAt": "2026-09-17 10:05:00",
    "reasonCode": null
  },
  "configReady": true
}
```

Media 批量查询整体失败时，RTMP 记录返回 `UNKNOWN/LIVEKIT_STATUS_STALE`，不得回退读取 Gateway 状态。

### 9.4 其他生命周期入口

- `restartVideo`：RTMP 只触发 Media 主动对账并返回状态，不发送 MQTT。
- `switchChannel`：RTMP 首期拒绝切换或原样返回 `main`，不得发 Gateway restart。
- `releaseIdleSession`：Media 对 RTMP 返回空 stop payload，Control 不发送 Gateway stop。
- Gateway 上线恢复、摄像头 RTSP 恢复：Media 返回的恢复命令必须排除 RTMP Runtime。

## 10. Bigscreen BFF 实现

### 10.1 Gateway 目录

`FixedCameraCatalogLeaseClient.normalize` 只把以下摄像头写入目录租约：

```text
enabled == true && protocolType == RTSP
```

Control 的 `FixedCameraCatalogLeaseService.normalize` 再做同样过滤，形成防御性边界。RTMP 地址和凭证绝不进入
目录 MQTT 载荷。

### 10.2 装备归一化

`PanoramaService.fixedCameraDevice` 按协议计算：

| 字段 | RTSP | RTMP |
| --- | --- | --- |
| `clientId` | `fixed-camera-gateway` | `livekit-ingress` |
| `defaultQuality` | 有子码流时 `sub`，否则 `main` | `main` |
| `configReady` | 至少一个 RTSP URL 且协议合法 | Media 已配置 Ingress |
| `gatewayId` | 当前 Gateway | `null` |
| `gatewayHealth` | 当前状态 | `UNKNOWN/NOT_APPLICABLE` |
| `streamHealth` | RTSP 探测状态 | Media RTMP Track 状态 |
| `playable` | 保持现有 RTSP 规则 | `enabled && configReady && streamHealth=AVAILABLE` |

RTMP `status`：可播放为 `online`，其余为 `offline`。`UNKNOWN` 不允许前端误点播放。

地图、装备列表、任务摄像头和路径摄像头继续复用现有 `cameraId` 数据，不新增 BFF 到 Media 的直接调用。

## 11. robot-ui 实现

首期不新增清晰度控件，只修改协议感知的状态判断：

- `gatewayHealth=NOT_APPLICABLE` 不作为 RTMP 阻断条件。
- RTMP `streamHealth=UNAVAILABLE` 显示“摄像头未推流”或对应 reasonCode 文案。
- RTMP `streamHealth=UNKNOWN` 显示“摄像头状态待确认”。
- RTMP 会话 `INTERRUPTED` 的操作是 `refresh-playback`，不是 `restart-source`。
- RTSP 继续显示 Gateway 离线和“重启视频源”行为。
- 所有固定摄像头继续隐藏控制中心、控制器、对讲和 PTZ 操作。
- Canvas 截图和现有录像按钮保持可用。

需要检查的共享入口至少包括装备选择、视频墙、地图弹窗、任务监控固定摄像头列表和断流遮罩，避免只修一个页面。

## 12. Gateway 实现

Gateway 本身不增加 RTMP 功能。只做协议隔离：

- BFF 不下发 RTMP 摄像头。
- Control 收到目录时再次丢弃非 RTSP 记录。
- Gateway 若因旧租约收到 `protocolType != RTSP`，也应忽略并记录一次警告，不探测、不拉流。
- RTMP 不产生 Gateway 在线、探测、进程退出或恢复告警。

其他用户已下发的 RTSP 目录租约可以在协议切换后最长残留现有 TTL。首期不为此新增分布式
tombstone 存储，但必须满足：

- 切换编排主动停止已运行的旧 Publisher。
- Media `publisher_mode=LIVEKIT_INGRESS` 后 Control 不再产生 Gateway start/restart。
- 旧目录只可短暂触发探测，不可启动 Publisher；TTL 到期后必须消失。

不在 Gateway 中增加 RTMP Publisher、Ingress Client 或 LiveKit 管理凭证。

## 13. 部署实现

### 13.1 LiveKit Server

在 `livekit.yaml` 增加：

```yaml
ingress:
  rtmp_base_url: rtmp://{公网域名或公网IP}:1935/live
```

此地址是 CreateIngress 返回推流 URL 的权威前缀。生产值必须由部署模板变量生成，不写死测试 IP。

### 13.2 Ingress Worker

新增 `deploy/docker/config/livekit/livekit-ingress.yaml`：

```yaml
log_level: info
api_key: "{{LIVEKIT_API_KEY}}"
api_secret: "{{LIVEKIT_API_SECRET}}"
ws_url: "{{LIVEKIT_INTERNAL_URL}}"
health_port: 7888
prometheus_port: 7889
rtmp_port: 1935
redis:
  address: "{{LIVEKIT_REDIS_ADDRESS}}"
  username: "{{LIVEKIT_REDIS_USERNAME}}"
  password: "{{LIVEKIT_REDIS_PASSWORD}}"
  db: {{LIVEKIT_REDIS_DB}}
cpu_cost:
  rtmp_cpu_cost: 2.0
  whip_cpu_cost: 2.0
  whip_bypass_transcoding_cpu_cost: 0.1
  url_cpu_cost: 2.0
```

Worker 与 LiveKit Server 必须使用同一 Redis 和 API 凭证。首期只暴露 TCP `1935`；健康和指标端口仅在
部署网络内部暴露。Java、Nginx 和 BFF 不代理 RTMP。

### 13.3 Compose 与离线包

- 首期使用维护窗口整体升级：先停止旧 Control、Media，再执行迁移并发布匹配版本的新 Media、Control。
- 禁止旧 Control + 新 Media、新 Control + 旧 Media 混跑；部署脚本必须把二者作为同一版本单元校验。
- `docker-compose.yml`、`docker-compose.host.yml`、构建模板同时增加 `livekit-ingress`。
- `media-service` 增加 `LIVEKIT_INGRESS_ENABLED` 和 `LIVEKIT_INGRESS_STATUS_STALE_SECONDS`。
- `.env.example` 必须同时锁定 `LIVEKIT_SERVER_IMAGE`、`LIVEKIT_INGRESS_IMAGE`、`LIVEKIT_EGRESS_IMAGE`
  的具体 tag；交付清单记录官方 index、目标平台 manifest digest 和离线包 SHA-256，三者不得使用 `latest`。
- 版本组合必须先在目标部署形态中验证 Redis RPC、Create/List/DeleteIngress、Room 发布和 Egress 录像，
  再写入以下交付基线；未填入实测值前本文不得改为“已确认，待开发”。

| 组件 | 镜像 tag | digest | 兼容性结果 |
| --- | --- | --- | --- |
| LiveKit Server | `livekit/livekit-server:v1.13.3` | index `sha256:483b8b7b5b0654f91f1e8bdc7b46fcd37fd9911612ecf627f97e3185a89825bd`；amd64 `sha256:8ef3ee244ded8477d5b40d9dff4b084e1809a9fa0e4d9ed8d29943b3c6322998`；arm64 `sha256:61a0901d894d8304984958ca26587c0e7c7594570336b2812c167dd388c63d12` | 通过 |
| LiveKit Ingress | `livekit/ingress:v1.5.0` | index `sha256:2e1d3fcf10bfaebddaea74dc8b965410cda6377ed154451361b86ab3a9ee9f99`；amd64 `sha256:984855a0eea5673acd673d56607dce53795a6eeab9c0452ddc379e4cb2bf2c09`；arm64 `sha256:2e0c513fd47a1c47b58862332d854d009fabaabef14c86baf61f80825cb2ce7a` | 通过 |
| LiveKit Egress | `livekit/egress:v1.13.0` | index `sha256:980ff439431df2c773573721ab6da19e15bdc1f049ab7cb80e87470bf174c12f`；amd64 `sha256:a3e61a70479694a5075cff3c081ab633f34d3bfa778adc6089935c96908b6550`；arm64 `sha256:a9189fd852f946bd8f04128a7079b9a40885133e654ba5182a3d8a98170effe4` | 通过 |

实测环境与结果：

- 环境：目标 x86_64 Linux 服务器，16 CPU、62 GiB 内存；测试 Server、Ingress、Egress 使用独立 Redis
  与临时端口，未接入生产 PSRPC 通道。
- `CreateIngress`、`ListIngress`、`DeleteIngress` 均成功；活动 RTMP 删除后发布连接断开，Ingress 列表清空。
- 1920x1080、30 fps、H.264 RTMP 输入成功发布三层 Simulcast，Room API 返回
  `320x180 / 640x360 / 1280x720` 且 `simulcast=true`。
- Egress v1.13.0 成功订阅该 Ingress Track，录制并正常停止；产物为 13.6 秒 H.264 Main、
  1280x720、30 fps MP4，Egress 视频输入队列丢帧数为 0。
- amd64、arm64 Ingress 离线包均已校验包内架构。压缩包 SHA-256 分别为
  `476d43bd84d9c29d5b2db1ff4ca2cecf17565ee035d40803e37a3e186c716d36` 和
  `1f6c090017395c6d78ca8906473c22fb7c0390b25631a62952fa4fb6b78a9e64`。
- 合成 GStreamer 测试源产生 sender report 时钟漂移告警，但未造成建连、三层发布或录像失败；
  正式设备联调仍需按第 15 章检查真实摄像头时间戳稳定性。
- 离线目录同时生成：

```text
deploy/docker/tool-images/amd64/livekit-ingress.tar.gz
deploy/docker/tool-images/arm64/livekit-ingress.tar.gz
```

- 安装脚本和镜像校验清单同步纳入 Ingress。
- 目标服务器无 GPU，先验证 1-2 路 1080p 三层，再压测到 4 路；未压测前不承诺更高并发。

### 13.4 摄像头端配置

按当前设备界面设置：

| 配置项 | 值 |
| --- | --- |
| 客户端 | `1` |
| 推送主码流 | 开启 |
| 主码流服务器地址 | Management 创建时返回的完整 `pushUrl` |
| 主码流音频 | 关闭 |
| 推送子码流 | 关闭 |
| 子码流服务器地址 | 留空 |
| 子码流音频 | 关闭 |
| 编码 | H.264 |
| 分辨率 | 1920x1080 |
| 帧率 | 30 fps |
| 码率类型 | CBR |
| 码率上限 | 3.5-4 Mbps |
| I 帧间隔 | 30 |

设备当前的 2560x1440、25 fps、VBR、4 Mbps、I 帧间隔 50 可以用于兼容验证，但正式接入按上表调整。
只使用一个“服务器地址”输入框，不把 URL 和 Stream Key 分开填写，也不同时开启主、子两路推流。

## 14. 配置项

Media：

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

开关语义固定为：`false` 禁止 Ingress POST 创建和 PUT 轮换，但 GET、DELETE、status-query、Webhook、
周期对账及孤儿清理继续可用。该开关不影响机器人和 RTSP Runtime，也不能阻止生产回滚清理已有 Ingress。

`ingress-admin-max-concurrency` 是每个 Media 实例的全局许可数，不是每 cameraId 限制。必须在打开数据库
事务前获取，获取超时返回 `FIXED_CAMERA_INGRESS_BUSY`，并在所有成功、异常和取消路径释放。
`ingress-admin-operation-timeout-ms` 是获得许可后的 Media 总预算；单次 LiveKit HTTP 超时取
`min(ingress-livekit-call-timeout-ms, 总预算剩余时间)`。生产配置必须满足第 5.2 节与 Hikari
连接池的比例约束，数据库锁与事务超时按第 5.3 节从该总预算动态派生，不新增独立的
静态超时配置。

Management Media Client 增加：

```yaml
eiop:
  media-service:
    fixed-camera-ingress-create-path: /internal/media/fixed-camera-ingresses
    fixed-camera-ingress-detail-path: /internal/media/fixed-camera-ingresses/{cameraId}
    fixed-camera-ingress-timeout-ms: ${FIXED_CAMERA_INGRESS_MEDIA_TIMEOUT_MS:8000}
```

现有 `endpointBaseUrl` 和服务身份 Header 继续复用。固定摄像头 Ingress 端点必须使用专用 8 秒 timeout，
不复用普通 Media Client 的 5 秒 timeout，也不调大其他 Media 调用的全局超时。同一 Management 请求内的
下游重试必须复用原 ingress revision/type；不得在缺少 fencing token 时自动重试。不得新增 Java 监听端口。

管理端前端 HTTP 客户端与 API Gateway 对 publish-config POST/PUT/DELETE 的超时必须不低于 12 秒。
已有全局超时若高于该值，只需验证，不新增重复配置；低于该值时只调整 publish-config 路由或请求。
固定摄像头普通 PUT 在协议切换时会串行调用 Control 和 Media，管理端前端对该 PUT 使用 20 秒超时；API
Gateway 对该路由的超时不得先于 Management 完成上述两次下游调用。

## 15. 日志、指标与安全

### 15.1 日志

允许字段：`cameraId`、`ingressId`、操作、结果、总耗时、`permitWaitMs`、`acceptTxMs`、`liveKitMs`、
`commitTxMs`、状态、reasonCode、异常类型、异常分类、SQLState 和 vendor code。各阶段耗时使用单调时钟计算，
不使用系统墙上时钟做 deadline。SQLState/vendor code 只记录数值化分类信息，不记录 SQL 文本和参数。

禁止字段：`streamKey`、完整 `pushUrl`、LiveKit API Secret、摄像头密码、Authorization Header、完整
LiveKit Ingress 响应。

### 15.2 指标

固定指标：

```text
media_fixed_camera_ingress_configured
media_fixed_camera_ingress_online
media_fixed_camera_ingress_reconcile_failures_total
media_fixed_camera_ingress_orphans_total{result}
media_fixed_camera_ingress_operations_total{operation,result}
media_fixed_camera_ingress_admin_inflight
media_fixed_camera_ingress_admin_rejected_total{operation}
media_fixed_camera_ingress_admin_permit_wait_seconds{operation,result}
media_fixed_camera_ingress_livekit_call_duration_seconds{operation,api,result}
media_fixed_camera_ingress_admin_duration_seconds{operation,result}
media_fixed_camera_ingress_session_restore_failures_total
media_fixed_camera_ingress_connection_evictions_total{result}
```

不得用 `cameraId` 或 `ingressId` 作为指标标签。

### 15.3 网络和凭证

- Ingress CRUD 只通过内部 Media API 调用，不经公网 BFF 暴露。
- RTMP `1935` 是设备推流入口，不是 Java REST 端口。
- Stream Key 是随机凭证，不能由 cameraId 派生。
- 首期 RTMP 为明文传输，部署文档必须明确网络风险；RTMPS 是后续范围。

## 16. 测试清单

### 16.1 Media 单元测试

- 首次 POST 创建并只返回一次凭证。
- 重复 POST 不重复创建、不返回旧凭证。
- 并发 POST 最终只有一个有效 `ingress_id`。
- 同 cameraId、同 token/type 的并发请求由 Runtime 行锁串行，最多只执行一次有效 CreateIngress。
- 全局 Ingress 管理许可在打开事务前获取；许可已满时返回 `FIXED_CAMERA_INGRESS_BUSY`，
  不占用数据库连接、不接纳 token、不调用 LiveKit。
- 全局许可在成功、业务异常、HTTP 超时、线程中断和取消路径均会释放，无许可泄漏。
- Hikari `maximumPoolSize < max(4, ingressAdminMaxConcurrency * 4)` 时启动校验失败；默认并发数 2 要求连接池至少为 8。
- List/Delete/Create 共享同一 5 秒 Media deadline；单次 LiveKit 调用不超过 3 秒和剩余预算的较小值。
- Ingress 专用 Runtime 锁使用剩余 deadline；不修改机器人和 RTSP 共用的悲观锁方法。
- 本地 deadline、MySQL `1205`、`1213` 和 SQLState `40001` 返回 `FIXED_CAMERA_INGRESS_BUSY`。
- SQLState `08` 类返回 `FIXED_CAMERA_INGRESS_UNAVAILABLE`；SQLState `23` 类、SQL 语法错误和未分类异常
  返回内部错误，不得映射为 busy。
- 外层 `PessimisticLockException`、`CannotAcquireLockException` 和 `QueryTimeoutException`
  不单独决定错误码，必须验证 cause/nextException 异常图与本地 deadline 上下文。
- Runtime 锁未获取时不调用 LiveKit，所有锁超时路径均释放全局并发许可。
- `livekitCallTimeoutMs <= ingressAdminOperationTimeoutMs`，两者必须为正数；不满足时 Media 拒绝启动。
- CreateIngress 在 LiveKit 已成功后返回超时；重试前对账并撤销孤儿，最终只有一个有效 Ingress。
- 服务重启后自动删除超过保护期的平台孤儿 Ingress，不删除无平台归属 metadata 的外部资源。
- 同一 cameraId 存在多个平台 Ingress 时收敛为一个；删除失败时不继续创建。
- CreateIngress 成功而数据库保存失败时执行补偿删除。
- GET/批量查询绝不返回 Stream Key。
- DELETE 的不存在、LiveKit 404、失败保留映射。
- PUT 轮换成功和新建失败后的未配置状态。
- PUT/DELETE 后 `ingress_id` 为空时 `publisher_mode` 仍为 `LIVEKIT_INGRESS`。
- Webhook Track 发布、丢失、恢复状态转换。
- 5 秒对账覆盖无人观看的 Runtime。
- LiveKit 查询失败 15 秒内保留、超时后 UNKNOWN。
- Media 重启或现有调度租约接管后从持久化字段恢复上次状态和 reasonCode。
- RTMP 新会话直接复制 Runtime Track 并进入 STREAMING。
- 固定摄像头创建会话的 `expectedPublisherMode/expectedPublisherRevision` 在 Runtime 锁内原子校验；不匹配时
  不创建或复用会话。
- Ingress POST/PUT/DELETE 成功或失败均不改变 `publisher_revision`；配置请求在调用 Media 前失败后，正常
  播放仍可使用原 publisher generation。
- 接纳事务成功、执行事务回滚后，`ingress_operation_revision/accepted_ingress_operation` 仍保留已接纳值。
- Ingress operation revision 的小于、等于同操作、等于不同操作和大于四种分支；大于时先持久化
  最高已接纳 token/type，外部调用失败后 token 不回退。
- 相同 token/type 的 CREATE、ROTATE、REVOKE 分别从“资源已存在但未映射”、“旧资源已删除但新资源未创建”、
  “删除未完成”状态继续补齐，不盲目 no-op，也不恢复旧 Stream Key。
- 较新 REVOKE 接纳但 LiveKit 删除或数据库回写失败后，迟到的较旧 ROTATE 仍被拒绝。
- 较旧 CREATE/ROTATE 在更高 token 接纳后才返回新 Ingress 时，二次校验不回写旧结果并执行补偿删除；
  补偿失败时周期对账最终清理过期 metadata 资源。
- Ingress CRUD、`quiesce`、`publisher-mode` 分别覆盖允许模式和错误模式；`publisher-mode`
  目标模式 no-op 不清 Track、不关闭新会话。
- `requestClientStart` 的公开调用、客户端上线恢复、自动恢复、手动重启和切码流入口都经过同一 Runtime
  mode/revision 门禁；`LIVEKIT_INGRESS` 绝不生成 Publisher Token 或 start command。
- 播放请求分别与 RTSP -> RTMP、RTMP -> RTSP 并发时，最终至多存在一种发布端，旧 revision 返回冲突。
- RTMP 空闲释放不删除 Room、不返回 stop payload。
- RTMP 不进入 fixed-camera recovery command。
- 轮换创建失败后不进入 interrupted restart、idle Gateway stop 或 Room 误删除分支。
- 统一对账调度器扫描活动会话与 `LIVEKIT_INGRESS` Runtime 并共用数据库租约。
- `ingressAdmin` 只存在于管理 Token。

### 16.2 Management 测试

- RTSP 和 RTMP 条件校验。
- 创建、查询、轮换、撤销接口权限和对象数据权限。
- `PermissionCatalog` 四条 publish-config 精确路由均优先命中；POST 的 create/edit 任一权限通过，其他方法
  按表中单权限判定，未授权和未分类请求均拒绝。
- 同一 Management 实例并发操作同一 cameraId 时由 keyed lock 串行；锁实现可回收且不存在无界增长。
- 两类 revision 分别使用数据库原子递增；配置操作只推进 ingress revision，协议切换只用 publisher
  revision 参与播放门禁。
- 同一 Management 请求内的下游重试复用同一 ingress revision/type；用户重新提交可领取更高 token。
- 超时后迟到的低 ingress revision 被 Media 拒绝，即使较新撤销最终失败，也不得重新创建 Ingress。
- 切换或删除过程中重启 Management 后，state 和两类 revision 保留；重新提交相同请求可从头幂等完成。
- RTSP -> RTMP 在保存目标档案前失败、RTMP -> RTSP 在保存目标档案前失败时，刷新编辑页仍按 state 还原
  正确目标协议；切回 RTSP 可以补录流配置并继续原操作，反向协议请求被拒绝。
- 非 `STABLE` 时普通编辑、启用/禁用、反向切换和播放均拒绝，前端能识别并重试原操作。
- 首次凭证已创建但响应丢失时不恢复明文，重试收口后只能通过 PUT 轮换获得新地址。
- RTMP 新增保存后自动生成配置失败时档案保留。
- RTMP 改 RTSP 时先撤销，撤销失败不更新协议。
- RTSP 改 RTMP 时必须先经 Control 停止旧 Gateway Publisher 并确认旧 Track 退出。
- RTMP 改 RTSP 时只有档案保存后才切换为 `FIXED_CAMERA_GATEWAY`。
- 删除关联校验先于运行态清理；RTMP Ingress 或 RTSP Publisher/Track 清理失败均不删档案。
- Direct Client 保留下游错误码并正确发送服务身份 Header。
- Media 返回白名单内 Ingress 业务码时，Management 保留业务码与 HTTP 状态并使用本地安全文案；未知错误体、
  非法错误体和网络故障统一映射为依赖服务不可用，不透传下游技术细节。
- Ingress 专用 Media Client 在 5 秒 Media 内部预算边界成功时不会提前超时，8 秒超时时才归一为结果不确定。
- 同一 Management 请求内的 HTTP 重试复用原 revision/type；新的用户请求才能领取更高 revision。

### 16.3 Control/BFF 测试

- RTSP 开始播放仍发送原 MQTT。
- RTMP 开始播放从不发送 MQTT。
- RTSP 删除关闭活动会话和录像，发送精确 Gateway stop 并确认 Participant/Track 退出后才允许删档案。
- `publisher-mode-switch` 和 `publisher-quiesce` 对同一 revision 幂等，对旧 revision 拒绝。
- `publisher-mode` 已为目标模式时无副作用成功；RTMP 删除先推进 publisher generation，再撤销 Ingress。
- Runtime 为 `LIVEKIT_INGRESS` 但 `ingress_id` 为空时仍不发送 MQTT。
- RTSP 首次播放创建 `FIXED_CAMERA_GATEWAY` Runtime；机器人所有 Runtime 创建入口都写入 `DEVICE_CLIENT`。
- RTMP 离线、未知、未配置分别返回约定错误。
- RTMP 不进入 Gateway 目录租约。
- RTMP 健康状态不依赖 Gateway。
- 超过 500 台 RTMP 摄像头时按 500 分批并保持顺序，单批失败只影响当批。
- BFF 的 `clientId/defaultQuality/playable/status` 映射正确。
- 地图、装备、任务和路径入口仍使用同一 cameraId。

### 16.4 机器人与 RTSP 共享路径回归

机器人回归：

- 新建 `DEVICE_CLIENT` Runtime 和 VideoSession，正常生成 Publisher Token，不触发任何 Ingress CRUD。
- `reuse=true` 复用已有会话、Room 和 Track，不重复创建 Runtime 或错误变更 `publisher_mode`。
- 主/子码流切换继续使用原机器人指令与 Publisher Token 链路。
- 客户端上线恢复、中断自动重启和手动重启均经过 `DEVICE_CLIENT` 门禁并恢复原有推流。
- audio-only 对讲会话升级为音视频后，视频 Track、Viewer Token 和停止语义不变。
- Viewer 全部退出后按现有空闲超时释放；存在活动录像时不得提前释放会话或 Room。
- 机器人 Runtime 的 `publisher_mode` 始终为 `DEVICE_CLIENT`、`publisher_revision` 始终为 `0`，
  不进入 Ingress CRUD、Ingress 对账、固定摄像头 Gateway 目录或 RTMP 空闲释放分支。

RTSP 固定摄像头回归：

- 正常开始播放、`reuse=true` 复用、Track 中断恢复、自动/手动重启仍执行
  `Control -> Media -> MQTT -> Gateway -> LiveKit`。
- 主/子码流切换仍向 Gateway 发送精确指令，不进入 Ingress 管理。
- Viewer 退出、普通停止和空闲释放继续使用原 Gateway stop 与 Room 清理语义；活动录像仍阻止提前释放。
- RTSP Runtime 始终为 `FIXED_CAMERA_GATEWAY`，正常播放、恢复和释放不创建、轮换或撤销 Ingress。

### 16.5 robot-ui 测试

- RTMP 在线可选、离线不可选、未知不可选。
- `NOT_APPLICABLE` 不显示 Gateway 离线。
- RTMP 断流显示等待恢复，不出现重启源动作。
- 恢复同一推流后播放器自动续播。
- 截图和录像可用，控制中心、控制器、对讲和 PTZ 不显示。
- publish-config POST/PUT/DELETE 使用不低于 12 秒的请求超时，5 秒 Media 边界成功不会被前端提前取消。

### 16.6 集成与部署测试

1. 创建 RTMP 摄像头并获得完整 `pushUrl`。
2. 使用真实摄像头唯一“服务器地址”输入框推流。
3. 验证 Ingress Worker、Room、固定 Participant 和三层 Track。
4. 从装备列表和地图播放。
5. 断开摄像头网络 20 秒，确认中断；恢复后自动续播。
6. 轮换地址，确认旧 Key 失效、新地址可用。
7. 撤销后旧地址无法推流。
8. 禁用再启用，确认 Ingress 未被删除。
9. 回归机器人视频和 RTSP 固定摄像头完整链路。
10. 分别验证 amd64、arm64 离线镜像可加载并启动。
11. 注入 CreateIngress 超时、重复 Ingress 和补偿删除失败，确认周期对账最终收敛。
12. 在已运行 RTSP Publisher 时执行 RTSP -> RTMP，确认旧进程和 Track 先退出。
13. 用超过 500 台的摄像头数据验证 Control 分批与部分失败降级。
14. 在播放请求与双向协议切换并发时验证不会生成双发布端或发送过期 MQTT start。
15. 删除正在播放的 RTSP 摄像头，确认 Publisher、Track、会话和录像均先收口，且目录 TTL 内不能续启。
16. 在双向切换和删除的各外部调用后重启 Management，确认相同请求复用原 revision 完整幂等恢复，
    冲突操作和旧 revision 被拒绝。
17. 验证部署脚本拒绝 Media/Control 版本不匹配；演练功能回滚后机器人和 RTSP 正常播放。
18. 如交付旧二进制回滚，演练停机、`publisher_mode` 改回可空、Media/Control 整体回滚和再次升级回填。
19. 注入 publish-config 在 Management 递增 ingress revision 后、调用 Media 前失败，确认原视频仍可新建会话。
20. 让较新撤销先接纳、再在 LiveKit 删除或数据库回写阶段失败，随后放行迟到的旧轮换请求；
    确认旧 token 始终被拒绝。
21. 让旧创建/轮换在 Media HTTP 客户端超时并释放执行行锁后，由 LiveKit 延迟生成资源；
    在此之前先接纳更高 token，确认过期 `ingress_id` 不回写，能定位时立即补偿删除，
    结果不确定或补偿失败时由对账任务最终删除。
22. 分别在 CREATE、ROTATE、REVOKE 的各个 LiveKit 副作用后重启 Media，使用相同 token/type 重试并确认
    从 metadata 和当前资源事实补齐目标状态。
23. 关闭 `LIVEKIT_INGRESS_ENABLED` 后确认 POST/PUT 被拒绝，GET/DELETE/对账清理仍可执行，再停止 Worker。
24. 以 Ingress 管理许可上限数量同时注入 5 秒 LiveKit 慢请求，另一请求在 200 ms 内返回
    `FIXED_CAMERA_INGRESS_BUSY`；同时验证机器人、RTSP 会话创建、Publisher Token、录像和空闲释放无连接池耗尽或显著退化。
25. 验证 Ingress 操作的所有成功、异常、超时和取消路径均释放并发许可，长时间压测后
    `media_fixed_camera_ingress_admin_inflight` 回到 0。
26. 注入接近 5 秒但在 Media 总预算内成功的操作，确认 Management 8 秒专用客户端和管理前端/API Gateway
    12 秒预算不会提前超时；再注入超过 5 秒的操作，确认 Media 主动终止并进入可对账的结果不确定状态。
27. 使用目标 MySQL 版本：事务 A 持有目标 Runtime 行锁，事务 B 执行 Ingress 专用加锁；
    确认 B 在 5 秒 deadline 内返回 `FIXED_CAMERA_INGRESS_BUSY`、不调用 LiveKit、不泄漏并发许可。
    分别覆盖“接纳事务获锁失败，token 未推进”和“token 已接纳后执行事务获锁失败，
    同 token/type 可重试”。
28. 验证 JPA lock timeout 在目标 Hibernate/MySQL 版本上的实际效果；如使用会话级
    `innodb_lock_wait_timeout`，确认连接归还 Hikari 前已恢复，后续机器人和 RTSP 事务不继承该值。
29. 分别注入 MySQL `1205`、`1213`、SQLState `40001`、`08xxx`、`23xxx`、SQL 语法错误和
    未知持久化异常，确认 cause/nextException 异常图分类与业务错误码一致；外层异常与底层 SQL
    根因冲突时按文档优先级分类，无法识别时不得回退为 `FIXED_CAMERA_INGRESS_BUSY`。
30. 构造 cause 与 `nextException` 混合链、环形异常图和超过最大节点数的异常图，确认分类器
    无死循环、无界遍历，且链中同时出现连接故障和锁超时时优先返回
    `FIXED_CAMERA_INGRESS_UNAVAILABLE`。
31. 注入 `innodb_lock_wait_timeout` 恢复失败，确认事务回滚、返回
    `FIXED_CAMERA_INGRESS_UNAVAILABLE`、当前物理连接被淘汰而非归还池中，新连接及后续机器人/RTSP
    事务不继承临时值；淘汰成功和失败均有日志与指标。

真实 MySQL 锁竞争测试落点为
`backend/src/test/java/com/robot/mediaserver/video/repository/FixedCameraIngressRuntimeLockMySqlIT.java`。
该测试仅在显式提供专用测试库时运行，日常单元测试不访问外部数据库：

```bash
MEDIA_MYSQL_IT_URL='jdbc:mysql://127.0.0.1:3306/media_lock_it?useSSL=false&serverTimezone=UTC' \
MEDIA_MYSQL_IT_USERNAME='root' \
MEDIA_MYSQL_IT_PASSWORD='仅测试库密码' \
mvn -f backend/pom.xml -Dtest=FixedCameraIngressRuntimeLockMySqlIT test
```

2026-09-17 已使用 MySQL `8.4.11`、MySQL Connector/J `9.7.0`、Hibernate `6.6.53.Final`
执行验证：事务 A 持有 Runtime 行锁时，事务 B 的 1 秒 Ingress 专用锁预算约 1 秒返回
vendor code `1205`、SQLState `40001`，异常分类为 `FIXED_CAMERA_INGRESS_BUSY`，未超过 5 秒 Media 总预算。
会话变量恢复与恢复失败连接淘汰由
`FixedCameraIngressRuntimeLockRepositoryTest` 覆盖；完整服务级“不得调用 LiveKit、许可必须释放”继续由
`FixedCameraIngressServiceTest` 和发布前压力场景共同验收。

## 17. 明确不做

首期禁止顺带实现：

- RTMPS、WHIP、SRT、HLS 拉流或任意 URL Ingress。
- 主、子两路 RTMP 或两个长期有效 Ingress。
- 无缝凭证轮换。
- 音频推流、播放或对讲。
- 新清晰度切换 UI。
- 新的任务、路径、地图或机器人关联表。
- RTMP 地址、Key 或 IP 推断业务归属。
- 新截图 Worker、新录像链路或跨 Track 自动续录。
- Gateway 承担 RTMP 或 Ingress 管理。
- HTTP 调试接口、模拟接口、演示回退或静态假数据。
- Media 读取 Management 数据库，或 BFF 直接调用 Media。
- 为本需求新增 Java 服务端口。

## 18. 开发顺序与提交边界

按以下顺序开发，每一步形成可验证的小闭环：

1. **部署前置（已完成）**：已锁定并实测 LiveKit Server/Ingress/Egress 兼容版本，填写兼容性基线，
   补齐 Ingress 配置、Compose、安装/打包脚本和双架构离线镜像。
2. **Media 数据层**：数据库迁移、Runtime 字段、Repository 和测试。
3. **Media 原子门禁**：expected mode/publisher revision、`requestClientStart` 最终校验和并发测试。
4. **Media Ingress 管理**：专用 Token、Twirp Client、CRUD、错误码和测试。
5. **Media 状态闭环**：Webhook、周期对账、状态批量查询、VideoSession Track 复用和生命周期隔离。
6. **Management 后端**：单实例 keyed lock、简化 state 与双 revision、精确权限规则、RTMP 协议校验、
   MediaServicePort、推流配置接口、协议切换和删除编排。
7. **Management 前端**：条件字段、一次保存生成地址、编辑页配置操作和一次性凭证展示。
8. **Control**：RTSP/RTMP 播放分流、协议切换/删除收口、状态聚合和生命周期隔离。
9. **BFF**：Gateway 目录过滤和 RTMP 装备状态映射。
10. **robot-ui**：协议感知状态与恢复交互，不新增清晰度入口。
11. **全链路验证**：RTMP 正常与异常场景，并回归机器人和 RTSP。
12. **文档收口**：同步接口文档、模块 README、部署说明和验收结果，把状态改为“已实施”。

每个提交只包含一个可验证闭环，不把 Management、Media、Control 和 UI 的大批改动压成单个不可回退提交。

## 19. 开发完成判定

以下条件全部满足才可宣称完成：

- 本文第 5 章接口、第 6.10 节 Media 原子门禁和第 7.5 节单实例串行/双 revision 均有自动化测试。
- RTMP 正常创建、播放、释放、断流恢复和重启流程不产生 Gateway MQTT；仅 RTSP -> RTMP 协议切换和
  RTSP 摄像头删除允许发送一次或多次幂等 Gateway stop。
- 无观看者时 RTMP 在线状态仍可由 Media 对账得到。
- CreateIngress 结果不确定、重复请求和服务重启后均能收敛到单一平台 Ingress。
- RTSP/RTMP 双向切换不同时保留两个发布端，失败时不误发 Gateway MQTT。
- Ingress CRUD 失败或超时不推进播放门禁 generation；Ingress fencing token 在 LiveKit 调用前接纳且永不回退。
- 迟到的低 token 配置请求不能覆盖更新操作；过期外部调用结果不回写，且创建的资源能被补偿/对账清理。
- 每个 Media 管理端点校验当前 `publisher_mode`；同 revision/type 重试根据 Runtime 和 metadata 补齐目标状态。
- Ingress 持锁执行事务受全局并发许可保护，许可数符合 Hikari 比例约束；慢 LiveKit 请求不得耗尽
  共享连接池或显著退化机器人和 RTSP 链路。
- 超时层级为 200 ms 许可等待、3 秒单次 LiveKit、5 秒 Media 总预算、8 秒 Management 专用客户端和
  不低于 12 秒的管理前端/API Gateway；外层不得先于内层超时。
- Management 单实例内同一摄像头请求串行；进程重启后相同切换/删除请求可依据 state 和两类 revision 幂等恢复。
- 删除 RTSP 摄像头前已停止活动 Publisher、Track、会话和录像，不依赖目录 TTL 自然清理。
- publish-config 四种方法命中明确权限规则，POST 的 OR 权限和对象数据权限均有自动化测试。
- Viewer 离开不会停止摄像头推流。
- 断流恢复无需管理端、Gateway 或用户点击重启源。
- 数据库、接口、日志和前端存储均未泄露凭证。
- 大屏所有固定摄像头入口行为一致。
- 第 16.4 节列出的机器人与 RTSP 固定摄像头共享路径回归全部通过。
- 生产迁移、配置模板、双架构离线镜像和回滚步骤齐全。

## 20. 官方实现依据

- [LiveKit Ingress API](https://docs.livekit.io/reference/other/ingress/api/)
- [LiveKit Ingress 转码配置](https://docs.livekit.io/transport/media/ingress-egress/ingress/transcode/)
- [LiveKit 自托管 Ingress](https://docs.livekit.io/transport/self-hosting/ingress/)
- [LiveKit Ingress 服务仓库](https://github.com/livekit/ingress)
- [LiveKit Ingress Protocol](https://github.com/livekit/protocol/blob/main/protobufs/livekit_ingress.proto)

## 21. 讨论结论追踪表

| 已确认事项 | 最终结论 | 实施章节 |
| --- | --- | --- |
| 用户现场不部署 Gateway | 摄像头/NVR 直接推到中心 Ingress | 3、6、13 |
| RTMP 如何关联地图和路径 | 只使用 cameraId，复用现有关系 | 3、10 |
| 是否新建接入模式字段 | Management 不新建；Media Runtime 增加生命周期必需的 `publisher_mode` | 3、4.1、6.3 |
| 是否新建 Media 映射表 | 不新建，扩展 `media_source_runtime` 必要运行态字段 | 4.1、6.3 |
| Management 是否经 Control 管理 Ingress | 普通 CRUD 直接复用 MediaServicePort；协议切换和 RTSP 删除经 Control 收口旧发布端 | 5、7.2、7.6、9.1 |
| Management 是否透传用户 Header | 不透传，使用现有服务身份 Header | 7.2 |
| 新增保存能否生成地址 | 一次点击，先保存档案再自动创建配置 | 8.2 |
| 是否需要完整 CRUD | 创建、脱敏查询、轮换、幂等撤销 | 5、6.4-6.7 |
| 管理端是否新增详情页 | 不新增，保持现有查看/编辑页面 | 8.1 |
| 管理端是否新增删除按钮 | 不新增，保留后端 DELETE | 7.5、8.1 |
| 摄像头只有服务器地址输入框 | 填完整 pushUrl | 5.1、13.4 |
| 是否推主、子两路 | 只推一路主码流 | 3、13.4 |
| 是否保留三层 Simulcast | 保留 1080p 三层 preset | 3、6.2 |
| 首期是否开放清晰度切换 | 不开放，RTMP 会话固定 main | 3、6.10、11 |
| 后续三类视频如何切换清晰度 | RTMP 切 Simulcast 层，RTSP/机器人切输入源 | 上位设计第 2、14 章 |
| 是否启用音频 | 不启用 | 3、6.2、13.4 |
| 摄像头启用/禁用是否操作 Ingress | 不操作，只影响新业务播放 | 7.5 |
| 凭证是否持久化 | 不保存明文，只在创建/轮换时显示一次 | 4、5、15 |
| 凭证遗失怎么办 | PUT 轮换，不允许 GET 取回 | 5.1、6.6 |
| Viewer 关闭是否停止摄像头 | 不停止，不删 Ingress/Room | 6.10 |
| 断流恢复是否需要重启命令 | 不需要，由 Webhook 和对账自动恢复 | 6.9、9.3、11 |
| Gateway 是否看见 RTMP 摄像头 | 不看见，BFF/Control/Gateway 三层隔离 | 10.1、12 |
| 截图、录像是否重做 | 不重做，复用 Canvas 上传和 Egress | 11、16 |
| 任务、路径是否新增关联逻辑 | 不新增，复用现有 path_fixed_camera | 3、10 |
| 固定摄像头是否显示控制操作 | 不显示控制中心、控制器、对讲和 PTZ | 11 |
| 是否新增 Java 端口 | 不新增，只开放 Ingress TCP 1935 | 13、17 |
| 是否支持 RTMPS | 首期不支持 | 15.3、17 |
| 是否增加调试/模拟接口 | 不增加 | 17 |
| 服务器是否直接上高并发 | 先 1-2 路，再压测到 4 路 | 13.3 |
| 能否用 `ingress_id` 判断 RTMP | 不能，只能使用持久化 `publisher_mode` | 4.1、6.10 |
| CreateIngress 超时如何处理 | 先 ListIngress 对账，撤销孤儿/重复资源后才可重建 | 6.4、6.9 |
| 是否新增 Ingress 对账调度器 | 不新增，扩展现有 LiveKit Track 对账并共用数据库租约 | 6.9 |
| 发布模式如何避免竞态 | 创建会话和 `requestClientStart` 都在 Media Runtime 锁内校验预期 mode/publisher revision | 4.1、6.10、9.2 |
| 配置操作如何避免迟到覆盖 | 操作前接纳最高 ingress token，副作用后二次校验，过期资源补偿/对账清理 | 4.1、5.2、6.4、7.5 |
| Ingress 长事务如何保护共享连接池 | 事务前获取全局许可；连接池至少为 `max(4, concurrency*4)` | 5.2、14、16.1、16.6 |
| 超时层级如何定义 | 200 ms 许可、3 s 单次 LiveKit、5 s Media、8 s Management、前端/网关至少 12 s | 5.2、7.2、14、16.6 |
| Management 如何串行化 | 首期单实例使用可回收 cameraId keyed lock；持久化切换/删除 state 和两类 revision | 4.2、7.5 |
| RTSP 摄像头删除如何收口 | Control/Media 关闭会话与录像、精确 stop 并核验 Track 退出后删档案 | 5.2、7.6、9.1 |
| publish-config 如何鉴权 | 四条精确路由；POST 使用 create/edit 的 anyOf 权限 | 7.4 |

## 22. 回滚边界

### 22.1 默认功能回滚

生产问题优先关闭 RTMP 能力，不回滚 Media/Control 二进制：

1. 管理端先隐藏并禁止 RTMP 新增、生成和轮换；已有 RTMP 档案保持原值，不伪造 RTSP URL。
2. 设置 `LIVEKIT_INGRESS_ENABLED=false`，禁止 Media POST/PUT；GET、DELETE 和对账清理仍可用。
3. 记录需要保留的 Ingress，或通过 DELETE 撤销需要清理的 Ingress。
4. 确认清理完成后停止 Ingress Worker。
5. 保持匹配版本的新 Media、Control、BFF 和 Gateway 运行，机器人与 RTSP 继续走新代码中的原链路。
6. 数据库新增字段和索引保留。

这是首期唯一支持的在线回滚方式。

### 22.2 旧二进制回滚边界

首期正式版**不承诺旧二进制回滚**，发布手册只声明第 22.1 节功能回滚。以下内容仅作为必须在维护窗口
重新制定并完成演练后才能启用的应急边界，不属于当前交付能力：

1. 停止 Control 和 Media，禁止继续创建 Runtime。
2. 撤销或隔离 RTMP Ingress，并隐藏所有 RTMP 业务入口。
3. 将 `media_source_runtime.publisher_mode` 改回可空；不能保留无默认值的 `NOT NULL` 后直接启动旧 Media，
   也不能增加 `DEFAULT DEVICE_CLIENT`，否则旧 RTSP 固定摄像头会被错误分类。
4. Media、Control 必须作为一个版本单元同时回滚；禁止旧 Control + 新 Media 或新 Control + 旧 Media。
5. BFF/robot-ui 按对应兼容版本回滚；Management 中 `protocol_type=RTMP` 数据保留但不可播放。
6. 再次升级前重新执行 Runtime 回填和非空收紧迁移。

停机后的最低数据库回滚语句为：

```sql
ALTER TABLE media_source_runtime
    MODIFY COLUMN publisher_mode VARCHAR(32) NULL;
```

其他新增列和索引可以保留，因为旧代码不会读取；再次升级时必须先按 `source_type` 回填期间产生的 `NULL`，
确认无空值后才能重新改为 `NOT NULL`。

未来若要支持旧二进制回滚，必须另行提供经过演练的 SQL 和操作手册。不得把“保留非空字段并直接回滚
Media”描述为安全方案，也不回退或重写机器人、RTSP 摄像头、截图、录像、地图和路径业务数据。

## 23. 评审关闭记录

截至 2026-09-17，方案评审按以下结论关闭，允许进入开发：

1. 已确认 publisher/ingress 双 revision、Ingress 最高已接纳 token、同 token 目标状态对账、
   过期外部副作用的二次校验与补偿清理、持锁操作的并发/连接池约束、递增超时层级、
   Media 端点模式前置条件、Management 单实例简化编排和
   RTSP 删除收口契约。
2. 已确认 publish-config 四条精确权限规则及 POST `anyOf(create, edit)` 的访问控制改造。
3. 第 13.3 节已填入 LiveKit Server、Ingress、Egress 的具体 tag、官方 digest 和真实兼容性测试结果。
4. EIOP 迁移脚本、OpenAPI DTO、前端 API 和跨仓库改动清单在对应开发提交中逐项实现并复核，
   不允许以兼容字段或临时接口替代。
5. 首期只支持第 22.1 节功能回滚；第 22.2 节旧二进制回滚明确不属于当前交付能力。
6. 已确认第 5.3 节的 Ingress 专用 Runtime 锁、会话变量恢复失败时的连接淘汰、
   cause/nextException 异常图遍历、按 deadline/SQLState/vendor code 的根因异常分类
   和目标 MySQL 集成测试可执行；
   测试通过属于开发和发布验收条件，不作为允许开始开发的前置条件。
