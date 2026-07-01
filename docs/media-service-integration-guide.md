# 媒体服务对接实操文档

## 1. 对接目标

边缘端上传任务产物，管理服务把产物绑定到任务执行记录，前端按任务执行记录展示视频和点位文件。

统一返回的文件 ID 字段都是 `fileId`。

请求头约定：

```http
X-Org-Id: org001
X-User-Id: manager-service
X-Roles: MEDIA_OPERATOR
```

## 2. 边缘端：上传视频

视频走 multipart，适合大文件和断点续传。

### 2.1 创建或恢复上传

```http
POST /api/media/files/multipart-uploads
Content-Type: application/json
```

```json
{
  "robotId": "robot-001",
  "deviceId": "camera01",
  "taskExecutionId": null,
  "sourceFileId": "robot-001/camera01/20260701/video-001.mp4",
  "fileType": "VIDEO",
  "fileName": "video-001.mp4",
  "contentType": "video/mp4",
  "fileSize": 1073741824,
  "metadata": "{\"name\":\"巡逻视频1\"}"
}
```

返回：

```json
{
  "fileId": "file_video_001",
  "uploadId": "upl_001",
  "partSize": 16777216,
  "partCount": 64,
  "uploadedParts": [],
  "partUrls": [
    { "partNumber": 1, "uploadUrl": "http://minio-presigned-url" }
  ]
}
```

边缘端必须保存 `fileId`，后续交给管理服务。

### 2.2 上传分片

对每个 `partUrl`：

```text
读取本地文件对应 partNumber 的二进制分片
PUT uploadUrl
PUT body = 分片原始二进制
HTTP 2xx = 该分片上传成功
```

示例：

```bash
dd if=video-001.mp4 bs=16777216 skip=0 count=1 2>/dev/null \
  | curl -X PUT --data-binary @- "http://minio-presigned-url"
```

没有可用 URL 或 URL 过期时重新取：

```http
POST /api/media/files/multipart-uploads/{uploadId}/part-urls
```

```json
{ "partNumbers": [17, 18, 19, 20] }
```

### 2.3 完成上传

所有分片 PUT 成功后：

```http
POST /api/media/files/multipart-uploads/{uploadId}/complete
```

视频完成上传后通常先返回 `PROCESSING`，媒体服务会异步生成 HLS。

```json
{
  "fileId": "file_video_001",
  "status": "PROCESSING",
  "ready": false
}
```

边缘端可轮询：

```http
GET /api/media/files/{fileId}/status
```

`status=READY` 后视频可播放。

## 3. 边缘端：上传点位文件

点位文件通常较小，走单接口上传。

```http
POST /api/media/files
Content-Type: multipart/form-data
```

表单字段：

```text
fileType=MAP
robotId=robot-001
deviceId=camera01
taskExecutionId=
sourceFileId=robot-001/task/tmp/points.json
metadata={"name":"点位文件"}
file=@points.json
```

返回：

```json
{
  "fileId": "file_point_001",
  "fileType": "MAP",
  "status": "READY"
}
```

边缘端把 `file_point_001` 交给管理服务。

## 4. 管理服务：绑定执行记录

管理服务拿到边缘端返回的多个视频 `fileId` 和一个点位文件 `fileId` 后，调用：

```http
POST /api/media/files/task-execution-binding
Content-Type: application/json
```

```json
{
  "taskExecutionId": "task_exec_001",
  "videoFileIds": ["file_video_001", "file_video_002"],
  "pointFileId": "file_point_001"
}
```

媒体服务只更新这些文件的 `media_file.task_execution_id`，不移动 MinIO 文件，不重新上传，不重新切片。

返回：

```json
{
  "taskExecutionId": "task_exec_001",
  "files": [
    { "fileId": "file_video_001", "fileType": "VIDEO", "taskExecutionId": "task_exec_001" },
    { "fileId": "file_point_001", "fileType": "MAP", "taskExecutionId": "task_exec_001" }
  ]
}
```

## 5. 管理服务：按执行记录查询

```http
GET /api/media/files?taskExecutionId=task_exec_001&status=READY&page=0&size=100
```

返回中按 `fileType` 区分：

```text
VIDEO：视频文件
MAP / CONFIG / OTHER：点位文件或其他任务产物
```

## 6. 前端：渲染视频

管理服务把第 5 步查到的数据返回给前端。

前端对每个 `fileType=VIDEO` 的文件调用管理服务播放接口；管理服务再转调媒体服务：

```http
POST /api/media/files/{fileId}/play-url
```

媒体服务返回：

```json
{
  "fileId": "file_video_001",
  "format": "hls",
  "contentType": "application/vnd.apple.mpegurl",
  "playUrl": "/api/media/files/file_video_001/hls/index.m3u8?token=xxx"
}
```

前端播放规则：

```text
Safari：video.src = playUrl
Chrome / Edge：使用 hls.js 加载 playUrl
```

点位文件可通过下载或内容接口读取：

```http
POST /api/media/files/{fileId}/download-url
GET  /api/media/files/{fileId}/content
```

## 7. 媒体服务已提供/需要提供

已提供：

```text
视频 multipart 上传
点位文件单接口上传
根据 taskExecutionId 查询文件
视频 HLS 播放地址签发
文件下载/内容读取
```

本次新增：

```text
POST /api/media/files/task-execution-binding
用于管理服务后补绑定 taskExecutionId
```
