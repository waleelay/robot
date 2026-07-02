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

Java 示例：

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class MultipartPartUploader {
    public void uploadPart(Path file, int partNumber, long partSize, String uploadUrl)
            throws IOException {
        long start = (partNumber - 1L) * partSize;
        long fileSize = Files.size(file);
        long size = Math.min(partSize, fileSize - start);

        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setFixedLengthStreamingMode(size);

        try (InputStream input = partInputStream(file, start, size);
             OutputStream output = conn.getOutputStream()) {
            input.transferTo(output);
        }

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("part upload failed, partNumber="
                    + partNumber + ", status=" + status);
        }
        // MinIO/S3 multipart PUT 通常无 JSON 响应体，可按需记录 ETag。
        String etag = conn.getHeaderField("ETag");
        conn.disconnect();
    }

    private static InputStream partInputStream(Path file, long start, long size) {
        try {
            InputStream input = Files.newInputStream(file);
            input.skipNBytes(start);
            return new BoundedInputStream(input, size);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int value = delegate.read();
            if (value != -1) remaining--;
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int read = delegate.read(b, off, (int) Math.min(len, remaining));
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
```

PUT 上传响应判断：

```text
成功：HTTP 200 / 204，通常没有 JSON 响应体，可读取响应头 ETag 作为日志。
失败：HTTP 4xx / 5xx，当前 part 视为失败；重新调用 part-urls 获取新的 uploadUrl 后重试。
注意：不要把 uploadUrl 当成媒体服务接口，它是对象存储地址。
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

请求示例：

```json
{
  "taskExecutionId": "task_exec_001",
  "videoFileIds": ["file_video_001", "file_video_002"],
  "pointFileId": "file_point_001"
}
```

字段说明：

```text
taskExecutionId：必填，管理服务的任务执行记录 ID。
videoFileIds：视频文件 ID 数组，可传多个。
pointFileId：点位文件 ID，通常是 MAP / CONFIG / OTHER。
```

媒体服务只更新这些文件的 `media_file.task_execution_id`，不移动 MinIO 文件，不重新上传，不重新切片。

成功响应：

```http
204 No Content
```

异常响应示例：

```json
{
  "timestamp": "2026-07-02 10:10:00",
  "code": "FILE_NOT_FOUND",
  "message": "未找到文件"
}
```

## 5. 管理服务：按执行记录查询

```http
GET /api/media/files?taskExecutionId=task_exec_001&status=READY&page=0&size=100
```

请求参数：

```text
taskExecutionId：必填，任务执行记录 ID。
status：建议传 READY，只取可展示/可下载/可播放文件。
fileType：可选。VIDEO 只查视频，MAP/CONFIG/OTHER 可查点位或其他任务产物。
page：页码，从 0 开始。
size：每页数量，最大 100。
```

响应示例：

```json
{
  "items": [
    {
      "fileId": "file_video_001",
      "robotId": "robot-001",
      "deviceId": "camera01",
      "taskExecutionId": "task_exec_001",
      "fileType": "VIDEO",
      "fileName": "video-001.mp4",
      "contentType": "video/mp4",
      "fileSize": 1073741824,
      "durationSeconds": 1800,
      "width": 1920,
      "height": 1080,
      "status": "READY",
      "videoStatus": "READY",
      "errorCode": null,
      "uploadedAt": "2026-07-02T10:00:00Z",
      "createdAt": "2026-07-02T09:30:00Z",
      "metadata": "{\"name\":\"巡逻视频1\"}"
    },
    {
      "fileId": "file_point_001",
      "robotId": "robot-001",
      "deviceId": "camera01",
      "taskExecutionId": "task_exec_001",
      "fileType": "MAP",
      "fileName": "points.json",
      "contentType": "application/json",
      "fileSize": 4096,
      "durationSeconds": null,
      "width": null,
      "height": null,
      "status": "READY",
      "videoStatus": null,
      "errorCode": null,
      "uploadedAt": "2026-07-02T10:01:00Z",
      "createdAt": "2026-07-02T10:01:00Z",
      "metadata": "{\"name\":\"点位文件\"}"
    }
  ],
  "page": 0,
  "size": 100,
  "total": 2
}
```

响应字段：

```text
items：文件列表。
fileId：媒体文件 ID，后续播放、下载、读取内容都用它。
fileType：VIDEO 表示视频；MAP / CONFIG / OTHER 可作为点位文件或其他任务产物。
status：READY 才能展示；PROCESSING 表示视频仍在 HLS 处理中；FAILED 表示处理失败。
videoStatus：仅视频有值，READY 后可调用 play-url。
durationSeconds / width / height：视频元数据，非视频为空。
metadata：上传时透传的业务 JSON 字符串。
page / size / total：分页信息。
```

管理服务返回给前端时按 `fileType` 区分：

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
