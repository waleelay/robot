# robot-mediaserver

具身智能装备集成管理平台媒体服务模块。当前工程覆盖实时视频链路的服务端、调试前端、机器人侧云接入客户端骨架和开发流程。

## 架构边界

```text
浏览器/Vue2 调试台
  -> Nginx HTTPS/WSS 入口
  -> Bigscreen BFF REST/WebSocket
  -> Center Control/Media Service REST/WebSocket
  -> EMQX 下发机器人媒体指令
  -> 机器人侧 Go 云接入客户端
  -> RTSP 双光云台
  -> LiveKit 发布 Track
  -> 浏览器订阅 LiveKit Room
```

核心边界：

```text
Bigscreen BFF: 面向大屏前端的 REST/WebSocket 入口，代理/聚合中心端接口，不承载媒体流
Center Control/Media Service: 会话编排、Token 签发、MQTT 指令、WebSocket 事件、抓拍任务、状态入库
Robot Client: RTSP 探测、可见光/热成像源选择、LiveKit 发布、MQTT ACK/状态上报
LiveKit: 实时媒体转发、多人订阅、Room/Track 生命周期事件
Frontend: 创建会话、订阅 Track、即时抓拍预览、调试事件查看
MinIO: 抓拍图片对象存储
MySQL: 会话、观看者、Track、抓拍、事件日志
Redis/Elasticsearch: 预留缓存、检索和统计扩展
```

## 工程结构

```text
backend/   Java 17 + Spring Boot 3 中心端控制/媒体服务
bigscreen-bff/ Java 17 + Spring Boot 3 大屏 BFF，面向前端代理中心端接口
frontend/  Vue2 + Element UI 实时视频调试台
client/    Go 机器人侧云接入客户端骨架
docs/      开发流程与联调说明
tools/     设计文档生成脚本
```

## 设计文档

```text
docs/realtime-video-interface-flow.md     实时视频三端交互与接口
docs/recorded-video-upload-design.md      巡逻视频上传与播放方案
docs/bigscreen-bff-panorama-design.md     大屏 BFF 抽层与全景地图接口方案
```

## 环境依赖

```text
JDK 17
Maven 3.9+
Node.js 18+
npm 9+
Go 1.24.4+
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
export MEDIA_SERVICE_BASE_URL='http://localhost:8088'
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
export ROBOT_BATTERY='82'
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
export GST_LAUNCH_PATH='gst-launch-1.0'
export AUDIO_CAPTURE_PIPELINE='autoaudiosrc ! audioconvert ! audioresample ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! fdsink fd=1'
export AUDIO_PLAYBACK_PIPELINE='fdsrc fd=0 ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! audioconvert ! audioresample ! autoaudiosink'
export CAMERA_CAMERA01_GROUP_TYPE='dual_gimbal'
export CAMERA_CAMERA02_GROUP_TYPE='body'
export CAMERA_CAMERA03_GROUP_TYPE='arm'
```

`ROBOT_BATTERY` 为 `0-100` 的电量百分比。`CAMERA_<CAMERA_ID>_GROUP_TYPE` 支持 `body`（本体）、`dual_gimbal`（双光云台）和 `arm`（机械臂）。

`LIVEKIT_URL` 用于后端和机器人客户端连接 LiveKit。前端在 HTTPS 入口下会自动改用同源 `wss://<当前主机>/livekit`；本机 HTTP 调试仍使用接口返回的 `LIVEKIT_URL`。

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
3. 启动中心端后端。
4. 启动大屏 BFF。
5. 启动机器人侧客户端。
6. 启动前端调试台或通过 Nginx 访问构建产物。
7. 在前端创建实时视频会话。
8. 检查 MQTT ACK/status、WebSocket 事件、LiveKit Track、抓拍任务。

## 局域网 HTTPS 通话

浏览器在非本机地址使用麦克风必须处于安全上下文。通过局域网 IP 访问时，使用 Nginx 提供 HTTPS、业务 WebSocket 的 WSS 代理，以及 LiveKit 的 WSS 信令入口。

示例以服务主机地址 `192.168.124.77` 为例：

```bash
cd frontend
npm run build

cd ..
sh deploy/nginx/generate-lan-cert.sh 192.168.124.77
```

将 [deploy/nginx/certs/server.crt](deploy/nginx/certs/server.crt) 安装为每台访问终端的受信任证书。证书必须包含实际使用的局域网 IP，否则浏览器不会允许麦克风权限。

后端启动前配置 LiveKit 内部地址：

```bash
export MEDIA_SERVICE_BASE_URL='http://localhost:8088'
export LIVEKIT_URL='ws://192.168.124.77:7880'
```

当前 Control 与 Media 模块运行在同一个后端进程中，`MEDIA_SERVICE_BASE_URL` 必须指向后端内部 HTTP 地址，不要配置为 Nginx 的 `https://<lan-ip>:4443` 浏览器入口。

大屏 BFF 默认监听 `8090`，并通过内部地址访问中心端；本地开发时可直接指向当前 `backend:8088`，不要让 BFF 再绕回 Nginx：

```bash
export CENTER_MANAGE_BASE_URL='http://localhost:8088'
export CENTER_CONTROL_BASE_URL='http://localhost:8088'
export CENTER_MEDIA_BASE_URL='http://localhost:8088'
export CENTER_CONTROL_WS_URL='ws://localhost:8088/ws/control'

cd bigscreen-bff
mvn spring-boot:run
```

在 macOS Docker Desktop 上启动 Nginx：

```bash
docker run --rm --name robot-mediaserver-nginx \
  -p 80:80 -p 4443:4443 \
  -v "$PWD/deploy/nginx/robot-mediaserver.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v "$PWD/deploy/nginx/certs:/etc/nginx/certs:ro" \
  -v "$PWD/frontend/dist:/usr/share/nginx/html/robot-mediaserver:ro" \
  nginx:alpine
```

访问入口：

```text
页面/API:        https://192.168.124.77:4443
业务 WebSocket:  wss://192.168.124.77:4443/ws/control
大屏 WebSocket:  wss://192.168.124.77:4443/ws/bigscreen
LiveKit 信令:    wss://192.168.124.77:4443/livekit
```

Nginx 配置默认通过 `host.docker.internal` 将 `/api/*`、`/ws/control`、`/ws/bigscreen` 转发到主机上的 `8090` 大屏 BFF，将 `/livekit/*` 转发到 `7880` LiveKit。BFF 再通过 `CENTER_*_BASE_URL` 访问中心端 `8088`，服务端到服务端链路不经过 Nginx。若 Nginx、BFF、中心端和 LiveKit 位于同一 Docker 网络，可将 [deploy/nginx/robot-mediaserver.conf](deploy/nginx/robot-mediaserver.conf) 中的 upstream 改为对应容器服务名。

前端在 HTTPS 页面中自动使用当前页面主机和端口下的 `/livekit` 公开前缀，因此通过 `:4443` 打开页面时会连接 `wss://<lan-ip>:4443/livekit`。LiveKit Web SDK 会在该地址后请求 `/rtc` 或 `/rtc/v1`，Nginx 会去除前缀后转发到 LiveKit。LiveKit 的 WebRTC 媒体端口仍需在局域网中可达；Nginx 只终止 HTTPS/WSS，不替代 LiveKit 的 UDP/TCP 媒体传输配置。

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
POST /internal/media/video-sessions
GET  /internal/media/video-sessions
GET  /internal/media/video-sessions/active
GET  /internal/media/video-sessions/{sessionId}
POST /internal/media/video-sessions/{sessionId}/token
POST /internal/media/video-sessions/{sessionId}/stop
POST /internal/media/video-sessions/{sessionId}/switch-channel
GET  /internal/media/video-sessions/{sessionId}/tracks
POST /internal/media/video-sessions/{sessionId}/snapshots
GET  /internal/media/video-sessions/{sessionId}/snapshots
GET  /internal/media/video-sessions/{sessionId}/events
POST /internal/media/snapshots/{snapshotId}/complete
POST /internal/media/snapshots/{snapshotId}/complete-file
POST /internal/media/snapshots/{snapshotId}/fail
POST /api/internal/livekit/webhook
WS   /ws/media
```

联调 Mock 接口：

```text
POST /internal/media/video-sessions/{sessionId}/_mock/client-acked
POST /internal/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}
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
当前画面截帧抓拍并上传
查询抓拍记录
预览抓拍图片
查询事件日志
模拟 ACK 和 Track published
```

## 机器人侧客户端

音频对讲使用实时 PCM 管线，不需要 `opusfile` 文件解码库。本地执行和构建必须携带 `-tags nolibopusfile`。

本地运行：

```bash
cd client
go mod download
go run -tags nolibopusfile ./cmd/robot-media-client
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
go build -tags nolibopusfile -o robot-media-client ./cmd/robot-media-client
```

Docker 构建：

```bash
cd client
docker build -t robot-media-client:dev .
```

当前客户端实现：

```text
订阅 start/stop/switch-channel 指令
订阅视频房间内的 intercom start/stop 指令
解析通道和清晰度
使用 ffprobe 探测 RTSP
发布 ACK/status
默认通过 gstreamer-publisher 作为 LiveKit Publisher 发布 Track
可通过 PUBLISHER_CMD 覆盖发布命令
通过内置 LiveKit 音频模块发布/订阅对讲 Track
通过 GStreamer 设备管线采集麦克风与播放扬声器 PCM
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
前端以 multipart/form-data 上传 JPEG
Control Server 转发到 Media Service
服务端创建抓拍记录并直接写入 MinIO
服务端更新 COMPLETED
WebSocket 推送 snapshot.requested/snapshot.completed
前端提示“抓拍已保存 查看”，点击查看图片
```

主路径接口：

```http
POST /api/control/video-sessions/{sessionId}/snapshots/file
```

```text
trackSid=TR_xxx
reason=manual_abnormal
remark=云台-可见光 手动抓拍
clientCapturedAt=2026-06-08T15:00:00.000Z
previewImageHash=123456
file=@snapshot.jpg
```

图片归档路径：

```text
snapshots/{robotId}/{deviceId}/{yyyy}/{mm}/{dd}/{snapshotId}.jpg
```

查询和预览：

```http
GET /api/control/snapshots?robotId=robot-001&deviceId=camera01&page=0&pageSize=20
GET /api/control/snapshots/{snapshotId}/image
```

内部抓拍完成接口仍保留：

```http
POST /internal/media/snapshots/{snapshotId}/complete
POST /internal/media/snapshots/{snapshotId}/complete-file
POST /internal/media/snapshots/{snapshotId}/fail
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
go build -tags nolibopusfile -o robot-media-client ./cmd/robot-media-client
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
