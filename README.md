# robot-mediaserver

具身智能装备集成管理平台媒体服务模块。当前阶段实现实时视频链路的后端微服务与 Vue2 调试前端。

## 工程结构

```text
backend/   Spring Boot Media Service
frontend/  Vue2 + Element UI 实时视频调试台
tools/     设计文档生成脚本
```

## 后端开发

后端使用 Java 17 + Spring Boot 3，第一版认证采用 Mock 请求头：

```http
X-User-Id: u1001
X-Org-Id: org001
X-Roles: MEDIA_VIEWER,MEDIA_OPERATOR
```

启动前按需提供环境变量：

```bash
export MYSQL_URL='jdbc:mysql://localhost:3306/robot_media?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='root'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export LIVEKIT_URL='ws://localhost:7880'
export LIVEKIT_API_KEY='devkey'
export LIVEKIT_API_SECRET='dev-secret-dev-secret-dev-secret-32'
export MQTT_BROKER_URL='tcp://localhost:1883'
export MQTT_ENABLED='false'
```

启动：

```bash
cd backend
mvn spring-boot:run
```

主要接口：

```text
POST /api/media/video-sessions
GET  /api/media/video-sessions/{sessionId}
POST /api/media/video-sessions/{sessionId}/token
POST /api/media/video-sessions/{sessionId}/stop
POST /api/media/video-sessions/{sessionId}/switch-channel
POST /api/media/video-sessions/{sessionId}/snapshots
WS   /ws/media
```

开发阶段提供两个 mock 接口，用于在云接入客户端未接入时推进状态：

```text
POST /api/media/video-sessions/{sessionId}/_mock/client-acked
POST /api/media/video-sessions/{sessionId}/_mock/track-published/{trackSid}
```

## 前端调试台

前端使用 Vue2 + Element UI + LiveKit JS SDK。

```bash
cd frontend
npm install
npm run serve
```

默认开发地址：

```text
http://localhost:8090
```

调试台功能：

- 输入 robotId / deviceId
- 选择 visible / thermal / fusion
- 选择 sub / main / auto
- 创建实时视频会话
- 连接业务 WebSocket
- 使用 LiveKit token 加入 Room
- 模拟客户端 ACK 和 Track published
- 停止会话
- 提交抓拍任务

## 当前设计确认

- 后端：Java Spring Boot 独立微服务
- 云接入客户端：Go + GStreamer + LiveKit SDK + MQTT client
- 中间件：LiveKit、EMQX、MySQL、Redis、MinIO、Elasticsearch
- 前端正式部署：Vue2 + Element UI 构建后由 Nginx 提供 HTTPS/WSS 入口
- 一期容量：最少 2 台机器人；设计按 16 路视频墙预留；单路详情最高 2K
- 抓拍：前端即时预览 + 服务端订阅 LiveKit Track 权威入库
