# robot-mediaserver

具身智能装备集成管理平台媒体服务模块。当前工程覆盖实时视频链路的服务端、调试前端、机器人侧云接入客户端骨架和开发流程。

## 架构边界

```text
浏览器/Vue2 调试台
  -> Media Service REST/WebSocket
  -> EMQX 下发机器人媒体指令
  -> 机器人侧 Go 云接入客户端
  -> RTSP 双光云台
  -> LiveKit 发布 Track
  -> 浏览器订阅 LiveKit Room
```

核心边界：

```text
Media Service: 会话编排、Token 签发、MQTT 指令、WebSocket 事件、抓拍任务、状态入库
Robot Client: RTSP 探测、可见光/热成像源选择、LiveKit 发布、MQTT ACK/状态上报
LiveKit: 实时媒体转发、多人订阅、Room/Track 生命周期事件
Frontend: 创建会话、订阅 Track、即时抓拍预览、调试事件查看
MinIO: 抓拍图片对象存储
MySQL: 会话、观看者、Track、抓拍、事件日志
Redis/Elasticsearch: 预留缓存、检索和统计扩展
```

## 工程结构

```text
backend/   Java 17 + Spring Boot 3 媒体服务
frontend/  Vue2 + Element UI 实时视频调试台
client/    Go 机器人侧云接入客户端骨架
docs/      开发流程与联调说明
tools/     设计文档生成脚本
```

## 环境依赖

```text
JDK 17
Maven 3.9+
Node.js 18+
npm 9+
Go 1.22+
FFmpeg/ffprobe
Docker Desktop
MySQL 8
Redis 7
EMQX 5
LiveKit Server
MinIO
Elasticsearch
```

当前已使用的本地容器名称：

```text
mysql
redis
emqx
livekit
minio
elasticsearch
```

## 环境变量

后端：

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/robot_media?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='root'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export LIVEKIT_URL='ws://localhost:7880'
export LIVEKIT_API_KEY='devkey'
export LIVEKIT_API_SECRET='dev-secret-dev-secret-dev-secret-32'
export LIVEKIT_ROOM_API_ENABLED='true'
export LIVEKIT_ROOM_EMPTY_TIMEOUT_SECONDS='60'
export LIVEKIT_ROOM_DEPARTURE_TIMEOUT_SECONDS='20'
export MQTT_ENABLED='true'
export MQTT_BROKER_URL='tcp://localhost:1883'
export MQTT_USERNAME=''
export MQTT_PASSWORD=''
export MINIO_ENABLED='true'
export MINIO_ENDPOINT='http://localhost:9000'
export MINIO_ACCESS_KEY='minioadmin'
export MINIO_SECRET_KEY='minioadmin'
export MINIO_BUCKET='robot-media'
```

前端：

```bash
export VUE_APP_API_BASE=''
export VUE_APP_WS_URL=''
```

机器人侧客户端：

```bash
export ROBOT_ID='robot-001'
export ROBOT_CLIENT_ID='robot-media-client-robot-001'
export MQTT_BROKER_URL='tcp://localhost:1883'
export MQTT_USERNAME=''
export MQTT_PASSWORD=''
export RTSP_VISIBLE_SUB='rtsp://192.168.124.204:8554/camera01'
export RTSP_VISIBLE_MAIN='rtsp://192.168.124.204:8554/camera01'
export RTSP_THERMAL_SUB='rtsp://192.168.124.204:8554/camera01'
export RTSP_THERMAL_MAIN='rtsp://192.168.124.204:8554/camera01'
export FFPROBE_PATH='ffprobe'
export PROBE_TIMEOUT_MS='8000'
export PUBLISHER_CMD=''
export GSTREAMER_PUBLISHER_PATH='gstreamer-publisher'
export GSTREAMER_PIPELINE='rtspsrc location={rtsp} protocols=tcp latency=100 ! queue ! rtph264depay ! h264parse config-interval=1'
```

`PUBLISHER_CMD` 支持占位符：

```text
{rtsp}
{livekitUrl}
{token}
{room}
{track}
```

## 启动顺序

1. 启动 Docker 中间件。
2. 确认 MySQL 库 `robot_media` 已存在。
3. 启动后端。
4. 启动机器人侧客户端。
5. 启动前端调试台。
6. 在前端创建实时视频会话。
7. 检查 MQTT ACK/status、WebSocket 事件、LiveKit Track、抓拍任务。

## 后端开发

启动：

```bash
cd backend
mvn spring-boot:run
```

构建：

```bash
cd backend
mvn -q -DskipTests package
```

Mock 请求头：

```http
X-User-Id: u1001
X-Org-Id: org001
X-Roles: MEDIA_VIEWER,MEDIA_OPERATOR
```

主要接口：

```text
POST /api/media/video-sessions
GET  /api/media/video-sessions
GET  /api/media/video-sessions/active
GET  /api/media/video-sessions/{sessionId}
POST /api/media/video-sessions/{sessionId}/token
POST /api/media/video-sessions/{sessionId}/stop
POST /api/media/video-sessions/{sessionId}/switch-channel
GET  /api/media/video-sessions/{sessionId}/tracks
POST /api/media/video-sessions/{sessionId}/snapshots
GET  /api/media/video-sessions/{sessionId}/snapshots
GET  /api/media/video-sessions/{sessionId}/events
POST /api/internal/media/snapshots/{snapshotId}/complete
POST /api/internal/media/snapshots/{snapshotId}/complete-file
POST /api/internal/media/snapshots/{snapshotId}/fail
POST /api/internal/livekit/webhook
WS   /ws/media
```

联调 Mock 接口：

```text
POST /api/media/video-sessions/{sessionId}/_mock/client-acked
POST /api/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}
```

媒体源接口：

```text
GET    /api/media/sources
POST   /api/media/sources
PUT    /api/media/sources/{sourceId}
DELETE /api/media/sources/{sourceId}
```

## MQTT 协议

服务端下发：

```text
robot/{robotId}/media/video/start
robot/{robotId}/media/video/stop
robot/{robotId}/media/video/switch-channel
```

客户端 ACK：

```text
robot/{robotId}/media/video/ack
```

```json
{
  "commandId": "cmd_xxx",
  "sessionId": "vs_xxx",
  "success": true,
  "message": "accepted",
  "timestamp": "2026-05-20T09:30:00+08:00"
}
```

客户端状态：

```text
robot/{robotId}/media/video/status
```

```json
{
  "sessionId": "vs_xxx",
  "status": "streaming",
  "trackSid": "TR_xxx",
  "trackName": "video.visible.sub",
  "message": "track published",
  "timestamp": "2026-05-20T09:30:05+08:00"
}
```

支持状态：

```text
acked
room_ready
publishing
streaming
track_published
interrupted
stopped
closed
failed
error
```

## 前端调试台

启动：

```bash
cd frontend
npm install
npm run serve
```

构建：

```bash
cd frontend
npm run build
```

默认开发地址：

```text
http://localhost:8090
```

功能：

```text
创建实时视频会话
保存媒体源 RTSP 配置
连接业务 WebSocket
订阅 LiveKit Room
可见光/热成像切换
停止观看
查询活跃视频墙列表
提交抓拍任务
查询抓拍记录
查询事件日志
模拟 ACK 和 Track published
```

## 机器人侧客户端

本地运行：

```bash
cd client
go mod download
go run ./cmd/robot-media-client
```

安装 LiveKit GStreamer Publisher：

```bash
cd client
sh scripts/install-gstreamer-publisher.sh
export PATH="$HOME/.local/bin:$PATH"
```

构建：

```bash
cd client
go build -o robot-media-client ./cmd/robot-media-client
```

Docker 构建：

```bash
cd client
docker build -t robot-media-client:dev .
```

当前客户端实现：

```text
订阅 start/stop/switch-channel 指令
解析通道和清晰度
使用 ffprobe 探测 RTSP
发布 ACK/status
默认通过 gstreamer-publisher 作为 LiveKit Publisher 发布 Track
可通过 PUBLISHER_CMD 覆盖发布命令
```

## LiveKit Webhook

配置地址：

```text
POST /api/internal/livekit/webhook
```

已处理事件：

```text
track_published      -> STREAMING
track_unpublished    -> INTERRUPTED
participant_left     -> robot participant 离开时 INTERRUPTED
room_finished        -> CLOSED
```

## 抓拍流程

```text
前端从正在播放的 LiveKit Track 截当前帧
前端提交抓拍任务
服务端创建 PROCESSING 抓拍记录
Snapshot Worker 订阅对应 Track 并生成正式图片
Snapshot Worker 上传 MinIO 或回写 objectKey
服务端更新 COMPLETED/FAILED
WebSocket 推送 snapshot.completed/snapshot.failed
```

JSON 回写：

```http
POST /api/internal/media/snapshots/{snapshotId}/complete
```

```json
{
  "officialObjectKey": "snapshots/2026/05/20/snap_xxx.jpg",
  "officialCapturedAt": "2026-05-20T10:30:00+08:00",
  "timeDeltaMs": 12
}
```

multipart 回写：

```http
POST /api/internal/media/snapshots/{snapshotId}/complete-file
```

失败回写：

```http
POST /api/internal/media/snapshots/{snapshotId}/fail
```

## 验证命令

```bash
cd backend
mvn -q -DskipTests package
```

```bash
cd frontend
npm run build
```

```bash
ffprobe -v error -rtsp_transport tcp -select_streams v:0 -show_entries stream=codec_name,width,height -of json 'rtsp://192.168.124.204:8554/camera01'
```

```bash
cd client
go build -o robot-media-client ./cmd/robot-media-client
```

## 当前开发状态

```text
服务端实时视频链路已实现
Vue2 调试前端已实现
机器人侧 Go 客户端已实现 MQTT/RTSP/GStreamer LiveKit Publisher 闭环
媒体源配置管理已实现
Snapshot Worker 已实现 ffmpeg 截帧和 MinIO 入库
Webhook Token 校验已实现
Nginx HTTPS/WSS 配置已补充
```
