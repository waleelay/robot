# EIOP 任务产物上传进度对接指南

> 状态：待实施
> 适用范围：EIOP Management、EIOP Control、EIOP 管理端
> 完整设计：[文件上传进度接口开发文档](../文件服务/文件上传进度接口开发文档.md)

## 1. EIOP 需要完成的内容

| 模块 | 必须完成 |
|---|---|
| Management | 根据执行记录聚合任务产物和 Media 文件上传进度，对外提供批量查询接口 |
| Control | 接受 Media 发布的执行记录失效事件，通过现有 STOMP 广播 |
| 管理端 | 在执行记录列表和详情中展示汇总及逐文件进度，收到事件后刷新当前页 |
| Bigscreen BFF | 验证现有代理能够转发新增 Management 接口；不实现进度聚合 |

EIOP 不需要实现以下内容：

- MinIO 分片事件接收。
- 分片字节数计算。
- Redis 分片去重。
- 文件合并和 HLS 处理。
- 浏览器到 Media 的 WebSocket。

以上能力由 Media Service 实现。

## 2. 整体调用链路

```text
机器人上传分片
    ↓
Media 计算文件进度
    ↓
Media 调用 EIOP Control 发布 task.changed.v1
    ↓
EIOP Control 通过现有 STOMP 通知管理端
    ↓
管理端批量请求当前页执行记录进度
    ↓
EIOP Management 查询执行记录及产物 fileId
    ↓
EIOP Management 批量调用 Media 内部进度接口
    ↓
EIOP Management 按 workflowInstanceId 聚合
    ↓
管理端展示执行记录汇总和每个产物进度
```

实时事件只表示“执行记录数据可能变化”，不携带文件 ID、执行记录 ID和百分比。真实状态以 Management 的 REST 响应为准。

## 3. Management 对接

### 3.1 新增公开接口

```http
POST /api/v1/management/task-workflow-instances/artifact-upload-progress-queries
Authorization: Bearer <user-token>
Content-Type: application/json
```

请求：

```json
{
  "workflowInstanceIds": [
    "twi_10001",
    "twi_10002"
  ]
}
```

约束：

- 一次最多查询 100 条执行记录。
- Management 必须校验现有 `task.execution.record.view` 权限和数据范围。
- 请求中存在无权访问的执行记录时，整个请求返回 `403002`。
- 前端不提交 `fileId`，避免绕过执行记录权限。

### 3.2 调用 Media 内部接口

Management 从已授权执行记录的 `videoResults`、`trackFileResults` 中提取非空 `fileId`，去重后调用：

```http
POST /internal/media/files/upload-progress-queries
Authorization: Bearer <management-media-service-token>
Content-Type: application/json
```

```json
{
  "fileIds": [
    "file_visible_001",
    "file_thermal_001"
  ]
}
```

Media 返回文件级进度：

```json
{
  "items": [
    {
      "fileId": "file_visible_001",
      "uploadId": "upl_001",
      "fileName": "visible.mp4",
      "fileType": "VIDEO",
      "phase": "UPLOADING",
      "uploadedBytes": 68157440,
      "totalBytes": 1073741824,
      "uploadedPartCount": 13,
      "partCount": 205,
      "uploadPercent": 6.34,
      "ready": false,
      "version": 18,
      "updatedAt": "2026-09-21 10:16:12",
      "errorCode": null,
      "errorMessage": null
    }
  ],
  "missingFileIds": [],
  "generatedAt": "2026-09-21 10:16:12"
}
```

Management 必须一次批量调用 Media，不能逐文件调用。

### 3.3 Management 对外响应

```json
{
  "available": true,
  "generatedAt": "2026-09-21 10:16:12",
  "instances": [
    {
      "workflowInstanceId": "twi_10001",
      "summary": {
        "status": "UPLOADING",
        "artifactCount": 2,
        "boundCount": 2,
        "pendingBindCount": 0,
        "uploadingCount": 2,
        "processingCount": 0,
        "readyCount": 0,
        "failedCount": 0,
        "missingCount": 0,
        "uploadedBytes": 94371840,
        "totalBytes": 2147483648,
        "uploadPercent": 4.39
      },
      "artifacts": [
        {
          "artifactKey": "VIDEO:visible-main:visible-001",
          "artifactType": "VIDEO",
          "mediaRef": "visible-001",
          "fileId": "file_visible_001",
          "mediaType": "VISIBLE",
          "serialNumber": "robot-001",
          "sourceId": "visible-main",
          "label": "可见光录像",
          "phase": "UPLOADING",
          "uploadedBytes": 68157440,
          "totalBytes": 1073741824,
          "uploadPercent": 6.34,
          "ready": false,
          "version": 18,
          "updatedAt": "2026-09-21 10:16:12",
          "errorCode": null,
          "errorMessage": null
        }
      ]
    }
  ]
}
```

### 3.4 聚合规则

1. 没有 `fileId` 的任务产物返回 `PENDING_BIND`。
2. Media 返回 `missingFileIds` 时，对应产物返回 `UNKNOWN/FILE_NOT_FOUND`。
3. Media 不可用时返回 HTTP 200、`available=false`，不能影响执行记录列表使用。
4. 执行记录总进度按字节加权：

```text
uploadPercent = sum(uploadedBytes) / sum(totalBytes) * 100
```

5. 没有 `fileId` 或文件大小未知的项不进入百分比分母，但计入 `pendingBindCount`。
6. 文件达到 `READY` 后才允许管理端请求播放地址。

### 3.5 Management 建议修改位置

- `TaskWorkflowInstanceController`：新增公开批量查询接口。
- Task Workflow 应用服务：新增执行记录产物进度聚合服务。
- 现有 `MediaServicePort`：新增批量查询文件进度方法。
- Media 基础设施适配器：调用 Media 内部接口。
- DTO：增加查询请求、执行记录汇总和产物进度响应。

应用层只能依赖 `MediaServicePort`，不能直接依赖 HTTP Client。

## 4. Control 对接

### 4.1 Media 调用入口

Media 复用 Control 现有内部实时事件接口：

```http
POST /internal/v1/control/realtime-events
Authorization: Bearer <media-control-service-token>
Content-Type: application/cloudevents+json
```

```json
{
  "specversion": "1.0",
  "id": "6195067081204172801",
  "source": "media",
  "type": "task.changed.v1",
  "data": {
    "scopes": ["EXECUTION"]
  }
}
```

### 4.2 Control 需要修改

1. 实时事件来源白名单增加 `media`。
2. 限制 `media` 只能发布：

```text
type = task.changed.v1
scope = EXECUTION
```

3. 复用现有 `source + id` 幂等去重。
4. 复用现有 STOMP 主题：

```text
/topic/platform/realtime-events
```

5. Control 不查询 Media、不计算进度、不补充 fileId。

## 5. 管理端对接

### 5.1 涉及页面

- `TaskExecutionRecordsPage.vue`
- `TaskExecutionRecordDetailPage.vue`

### 5.2 列表页

1. 完成原执行记录分页查询。
2. 收集当前页 `workflowInstanceId`。
3. 调用一次 Management 批量进度接口。
4. 增加“产物上传”列，展示：

| 状态 | 建议文案 |
|---|---|
| 无产物 | `--` |
| `PENDING_BIND` | `等待文件关联` |
| `UPLOADING` | `2/3，63.42%` |
| `PROCESSING` | `上传完成，处理中` |
| `READY` | `3/3 已完成` |
| 有失败 | `1 个失败` |
| `available=false` | `状态暂不可用` |

5. 原录像/产物弹窗展示每个文件独立进度，不用 `robotId` 合并。

### 5.3 实时刷新

管理端订阅现有 STOMP 主题。收到以下事件时刷新：

```text
source = media
type = task.changed.v1
scopes 包含 EXECUTION
```

刷新规则：

1. 事件校验器允许 `source=media`。
2. 1 秒防抖，将连续事件合并。
3. 只刷新当前页进度，不重新查询整个执行记录列表。
4. 同一时间只允许一个进度请求。
5. 请求期间再次收到事件时，请求结束后最多补一次刷新。
6. STOMP 重连后重新加载列表和当前页进度。

### 5.4 详情与回放

1. 页面打开时查询该执行记录的产物进度。
2. `UPLOADING` 显示百分比。
3. `FINALIZING/PROCESSING` 显示“处理中”。
4. 只有 `READY` 才请求播放地址。
5. 某个产物首次变为 `READY` 时刷新一次 replay，不能每个分片都刷新 replay。

## 6. Bigscreen BFF 配合项

Management 新接口对应的大屏地址：

```http
POST /api/bigscreen/business/tasks/execution-records/artifact-upload-progress-queries
```

BFF 只需要：

1. 确认现有执行记录通配代理能够转发该 POST 子路径。
2. 透传登录身份、请求体和 Management 错误响应。
3. 增加代理测试。

BFF 不需要连接 Media，也不需要实现上传进度 WebSocket。

## 7. 配置

Management 增加：

```text
MEDIA_SERVICE_BASE_URL
MEDIA_SERVICE_TOKEN
MEDIA_PROGRESS_CONNECT_TIMEOUT_MS=500
MEDIA_PROGRESS_READ_TIMEOUT_MS=2000
MEDIA_PROGRESS_MAX_WORKFLOW_INSTANCES=100
MEDIA_PROGRESS_MAX_FILE_IDS=500
```

Control 增加或复用：

```text
Media 服务身份凭证
Media 实时事件来源白名单
```

凭证必须通过部署配置注入，不能写入源码默认值。

## 8. 联调顺序

1. Media 提供内部批量进度接口和测试 fileId。
2. Management 完成 Media Client 和执行记录聚合接口。
3. 管理端先通过 REST 展示进度，不依赖实时事件。
4. Control 放开 `source=media` 并完成 STOMP 转发。
5. 管理端接入事件防抖刷新。
6. BFF 验证代理后，大屏使用同一响应模型接入。
7. 使用同一执行记录同时上传可见光和热成像视频，验证两条进度独立。

## 9. 验收清单

- Management 无 N+1 SQL 和逐文件 Media 调用。
- 无权执行记录不能通过进度接口查询。
- Media 故障不影响执行记录基础列表。
- 管理端和大屏对相同执行记录显示一致。
- 可见光、热成像等多个文件不会互相覆盖。
- STOMP 高频事件被前端合并，页面不会频繁刷新整个列表。
- `READY` 前不请求播放地址。
- Control 不承载文件进度数据和任务产物聚合逻辑。
