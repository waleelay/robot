# robot-mediaserver

具身智能装备集成管理平台的媒体与控制相关单仓库。仓库包含大屏接入层、控制编排、媒体服务、机器人侧客户端和两个前端；浏览器通过 LiveKit 直接收发媒体流，BFF 和 Control 只处理鉴权、业务编排、状态与信令。

## 1. 当前架构

```text
浏览器 / robot-ui / frontend
  -> Bigscreen BFF :8090（JWT 验证、REST 代理与聚合、WebSocket 桥接）
  -> Control Service :8082（控制会话、设备状态、MQTT 与媒体编排）
  -> Media Service :8088（视频会话、LiveKit、文件、TTS）

Control Service -> Management Service（设备档案、能力、固定摄像头）
Control Service <-> EMQX <-> 机器人客户端 / 固定摄像头 Gateway
Bigscreen BFF -> Control Service -> EMQX -> 固定摄像头 Gateway（用户授权短租约目录）
RTMP 固定摄像头 -> LiveKit Ingress -> LiveKit（不经过 Gateway）
Media Service -> LiveKit Server / Ingress / Egress / MinIO / MySQL
浏览器 <-> LiveKit（WebRTC 媒体流不经过 BFF 或 Control）
```

服务职责边界：

| 模块 | 当前职责 |
| --- | --- |
| `bigscreen-bff/` | 大屏统一认证入口、下游代理、全景聚合、统计报告、WebSocket 事件适配 |
| `control-service/` | `/api/control/**`、控制租约、设备命令、机器人状态、固定摄像头、MQTT、视频编排 |
| `backend/` | 视频会话与对讲、LiveKit Room/Token/Ingress/Egress、通用文件、HLS、TTS |
| `media-common/` | Media 与 Control 共享的纯 DTO 与枚举契约模块，不承载业务逻辑 |
| `fixed-camera-gateway/` | Go 固定摄像头 Gateway；RTSP、LiveKit、MQTT、健康探测与推流进程管理 |
| `python-client/` | Python 机器人客户端与演示模拟 |
| `robot-ui/` | 指挥中心前端 |
| `frontend/` | 实时视频和文件能力调试前端 |

Media Service 不发布 MQTT；Control Service 不保存媒体文件或承载媒体流；BFF 不复制 Control/Media 的核心业务。

## 2. 工程结构

```text
backend/          Java 17 + Spring Boot 3 Media Service
media-common/     Media/Control 共享 DTO 与枚举契约（纯 Java 17）
control-service/  Java 17 + Spring Boot 3 Control Service
bigscreen-bff/    Java 17 + Spring Boot 3 Bigscreen BFF
fixed-camera-gateway/ Go 固定摄像头 Gateway
python-client/    Python 机器人客户端及演示模拟
frontend/         Vue 2 实时视频调试前端
robot-ui/         Vue 指挥中心前端
deploy/           Docker、Nginx 与离线部署资源
scripts/          仓库级开发检查脚本
docs/             需求、设计、接口与协议、测试与验收
```

各模块的代码结构、配置和启动方式放在模块自己的 README；文档统一入口为 [docs/README.md](docs/README.md)，Java 服务接口入口为 [Java 服务接口总览](docs/03-接口与协议/Java服务接口总览.md)。

新增开发和存量整改遵循 [Alibaba 与 OpenAPI 开发规范](docs/02-设计/工程规范/Alibaba与OpenAPI开发规范.md)；已启用 Java 规则和三个服务的 HTTP 契约检查，命令及覆盖限制见[自动检查说明](quality/README.md)，最新验证见[规范整改执行记录](docs/04-测试与验收/执行记录/规范整改执行记录-20260929.md)。

存量问题、HTTP 入口清单及批次验收见[规范整改台账](docs/02-设计/工程规范/存量规范整改台账-20260929.md)。本轮使用本地检查和审查，CI 平台接入保留为后续选项。

大屏查询迁移的目标边界、接口方案与分阶段验收见[管理端接口对接优化说明](docs/03-接口与协议/大屏BFF/管理端接口对接优化说明.md)；该文档为评审中方案，不代表当前接口或部署已完成迁移。

## 3. 环境要求

```text
JDK 17
Maven 3.9+
Node.js 18+ / npm 9+
Go 1.24+
Python 3
Docker
MySQL 8
EMQX 5
LiveKit Server / Ingress / Egress
MinIO
FFmpeg / ffprobe
```

Redis 和 Elasticsearch 已配置依赖，但当前 Java 业务主链路不以它们作为权威数据源。

## 4. 本地启动

建议顺序：

1. 启动 MySQL、EMQX、LiveKit、MinIO 等依赖。
2. 启动 `backend`，默认端口 `8088`。
3. 启动 `control-service`，默认端口 `8082`。
4. 启动 `bigscreen-bff`，默认端口 `8090`。
5. 启动机器人客户端或固定摄像头 Gateway。
6. 启动 `robot-ui`、`frontend`，或通过 Nginx 访问构建产物。

Java 服务：

```bash
(cd media-common && mvn install)   # 共享契约模块需先安装到本地仓库
(cd backend && mvn spring-boot:run)
(cd control-service && mvn spring-boot:run)
(cd bigscreen-bff && mvn spring-boot:run)
```

固定摄像头 Gateway 的本地验证：

```bash
(cd fixed-camera-gateway && go test ./...)
(cd fixed-camera-gateway && go build -o fixed-camera-gateway ./cmd/fixed-camera-gateway)
```

完整配置分别见 [Media Service README](backend/README.md)、[Control Service README](control-service/README.md) 和 [Bigscreen BFF README](bigscreen-bff/README.md)。生产环境必须覆盖示例密钥、对象存储凭据、JWT Issuer、服务地址和允许跨域来源，不能直接使用仓库中的开发默认值。

配置整理后的 HLS 超时名称与 Control 扫描周期名称优先使用新入口，旧名称保留兼容回退；HLS 超时空值按未配置处理。迁移方式见上述模块 README 的配置章节。BFF 下游配置类统一为 `DownstreamServiceProperties`，外部 `center.*` 配置键保持不变。

## 5. 生产鉴权与访问边界

生产 REST/WebSocket 请求应先进入 Bigscreen BFF。BFF 验证 JWT 后覆盖 `X-User-Id`、`X-Org-Id`、`X-Roles` 并传给下游；Control 和 Media 当前信任这些受控 Header，自身不独立验签 JWT，不能直接暴露到不可信网络。

开发环境可使用以下 Header 直连下游：

```http
X-User-Id: u1001
X-Org-Id: org001
X-Roles: MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR
X-Client-Id: web-1
```

调用身份解析器的 Control 接口默认要求 `X-User-Id` 和 `X-Roles`；仅显式设置 `CONTROL_AUTH_ALLOW_DEFAULT_USER=true` 时允许开发默认身份。Media 的身份解析器目前仍有开发默认值；部分内网接口不调用身份解析器，不能由此推定所有接口均已校验身份。完整规则见 [Java 服务接口通用约定](docs/03-接口与协议/公共约定/Java服务接口通用约定.md)。

## 6. 当前核心能力

- 机器人摄像头和固定摄像头视频会话创建、复用、恢复、切换、停止与视频墙。
- LiveKit viewer/publisher Token、Webhook 与 Room API Track 事实对账、语音对讲，以及支持刷新恢复、离线收口和最长时长保护的 Egress 手动录像。
- 机器人排他控制租约、设备能力校验、通用设备命令和多合一设备控制。
- 本体运动由前端提交平台语义速度，Control 按设备控制画像限幅并转换为客户端使用的嵌套运动向量。
- 固定摄像头管理端档案校验、主/子码流选择及 Gateway MQTT 编排；大屏用户授权后由 Control
  下发短租约目录。
- 机器人在线/设备状态合并、WebSocket 广播和任务失效通知。
- 通用文件简单上传、分片直传、批量删除、下载、HLS 转码和播放。
- Media OpenTTS 生成/二进制广播，以及多合一设备文本 TTS 命令。
- 大屏设备、任务、告警聚合，业务白名单代理和本地 PDF 统计报告。

当前代码没有媒体源 CRUD、独立 RTSP 探测 REST API 或服务端 Snapshot Worker。抓拍由前端从 LiveKit Track 截帧，再按普通 `IMAGE` 文件上传；Media 以 LiveKit Webhook 触发并由 Room API 精确核对预期 Publisher/Track，设备状态上报不再直接作为 `STREAMING` 事实。

## 7. 通用文件与前端抓拍

前端抓拍是通用文件流程，不存在视频会话专用抓拍 API：

```text
前端从当前 LiveKit Track 截帧
  -> POST /api/control/files
  -> Control 代理到 Media
  -> Media 写入 MinIO，并创建 READY 的 IMAGE 文件记录
  -> 前端按 fileId 获取 inline 预签名地址后预览
```

简单上传在 BFF 和 Control 中使用输入流转发；图片预览、普通下载和视频播放分别复用既有
`download-url`、`play-url`，避免文件正文跨 Media、Control、BFF 重复缓冲。兼容的正文/HLS
对象代理限制单对象最大 32 MiB，不改变机器人直连 8088 文件接口的路径、鉴权或上传协议。

示例：

```bash
curl -X POST http://localhost:8090/api/control/files \
  -H "Authorization: Bearer <token>" \
  -F "file=@snapshot.jpg;type=image/jpeg" \
  -F "fileType=IMAGE" \
  -F "robotId=robot-001" \
  -F "deviceId=camera01" \
  -F 'metadata={"type":"snapshot","channel":"visible","sessionId":"vs_xxx","trackSid":"TR_xxx"}'
```

`sourceType`、`sessionId`、`trackSid` 和备注不是简单上传的独立表单字段；需扩展的信息统一放入 `metadata` JSON。

## 8. 局域网 HTTPS/WSS

非 `localhost` 页面调用麦克风必须使用安全上下文。Nginx 可统一暴露页面、REST、业务 WebSocket 和 LiveKit WSS：

```text
https://<host>:4443/          页面与 REST
wss://<host>:4443/ws/control 业务 WebSocket
wss://<host>:4443/ws/bigscreen 大屏兼容 WebSocket
wss://<host>:4443/livekit     LiveKit 信令
```

Nginx 只代理 LiveKit 信令，不替代 LiveKit UDP/TCP 媒体端口。Control 的 `MEDIA_SERVICE_BASE_URL`、BFF 的 `CENTER_*_BASE_URL` 应指向内部服务地址，不要绕回浏览器 HTTPS 入口。Docker 部署时在 `.env` 中设置 `NGINX_TLS_HOST`，`install.sh` 会自动生成对应的自签名证书；生产环境仍建议替换为受信任证书。

## 9. 验证

装备弹窗全部装备字段按需详情初始化、运行态版本及未知值规则见[大屏字段映射](docs/03-接口与协议/大屏BFF/大屏BFF字段来源映射文档.md)；
本地竞态回归入口为 `robot-ui/test/robot-popup-state.test.mjs`。
任务计划状态、按钮与事件收敛规则见[大屏 BFF 接口文档](docs/03-接口与协议/大屏BFF/大屏BFF接口文档.md)；
任务状态、地图及轨迹竞态回归入口为 `robot-ui/test/panorama-map-state.test.mjs`。

按改动范围执行最小充分验证；仓库级快速检查：

Java 规则及 HTTP 契约检查使用 `sh scripts/quality-check.sh all --base HEAD`；这里的 `HEAD` 仅用于当前未提交修改，分支审查应指定实际比较目标。省略 `--base` 时 Java 存量问题也会导致失败。
以下脚本是构建检查，包含四个 Java 模块、调试前端和固定摄像头 Gateway；需要已安装 Go，不替代规则、接口或业务测试。

```bash
sh scripts/dev-check.sh
```

常用模块命令：

```bash
(cd backend && mvn test)
(cd control-service && mvn test)
(cd bigscreen-bff && mvn test)
(cd python-client && python -m unittest discover -s tests)
(cd frontend && npm run build)
(cd robot-ui && npm run lint && npm run build:prod)
```

接口、事件载荷、配置、模块职责或启动方式变化时，应同步更新模块 README 与 `docs/` 中的权威专项文档。
