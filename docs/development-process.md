# 实时视频模块开发流程

## 分支与提交

```text
分支前缀: codex/
提交信息: 简体中文
提交粒度: 一个闭环能力一次提交
提交前验证: 后端构建、前端构建、必要联调命令
```

## 每日开发顺序

1. 查看工作区状态。
2. 启动 Docker 中间件。
3. 确认环境变量。
4. 构建后端。
5. 构建前端。
6. 验证 RTSP。
7. 启动后端。
8. 启动机器人侧客户端。
9. 启动前端调试台。
10. 执行实时视频主流程。
11. 记录失败点。
12. 提交代码。

## 主流程验收

```text
前端创建会话
后端创建 Room
后端签发 publisher/viewer token
后端发布 MQTT start
客户端 ACK
客户端 ffprobe RTSP
客户端发布 LiveKit Track
客户端上报 streaming
后端状态变更 STREAMING
前端订阅视频
多人复用同一路会话
停止最后一个观看者
后端进入 IDLE_WAIT
空闲释放后下发 stop
客户端停止推流
后端关闭会话
```

## 视频墙验收

```text
至少 2 台机器人
最多 16 路活跃视频列表
视频墙默认 sub
单路详情可切 main
同一路多人观看不重复推流
同一路状态、Track、观看人数一致
```

## 双光通道验收

```text
visible.sub
visible.main
thermal.sub
thermal.main
switch-channel 重新下发 publisher token
切换后前端可重新订阅目标 Track
RTSP 失败时会话进入 FAILED
```

## 抓拍验收

```text
未观看时抓拍按钮不可用
观看中抓拍按钮可用
前端从当前 video 画面截取 JPEG
请求为 multipart/form-data，包含 trackSid 和 file
服务端创建抓拍记录并直接写入 MinIO
WebSocket 推送 snapshot.requested
服务端更新 COMPLETED
抓拍成功提示包含“查看”链接
图片预览接口返回 image/jpeg
MinIO 存在正式图片
兜底抓拍失败时回写 FAILED
```

## WebSocket 验收

```text
/ws/media 可连接
/ws/control 可通过 Nginx 的 wss://<lan-ip>:4443/ws/control 连接
不会与 Vue dev-server websocket 冲突
会话状态事件可收到
抓拍事件可收到
断开后前端状态可恢复
```

## 局域网通话验收

```text
浏览器通过 https://<lan-ip>:4443 打开页面且证书受信任
后端配置 MEDIA_SERVICE_BASE_URL=http://localhost:8088 供 Control 内部调用 Media
后端配置 LIVEKIT_URL=ws://<lan-ip>:7880 供后端和机器人使用
前端在 HTTPS 页面自动使用 wss://<lan-ip>:4443/livekit
浏览器可授权麦克风并经 4443 建立通话
观看视频时开始和挂断对讲不重启共享视频 Track
```

## LiveKit 验收

```text
Room 创建成功
publisher token 仅允许发布
viewer token 仅允许订阅
Track 名称格式 video.{channel}.{quality}
robot participant 离开触发 INTERRUPTED
room_finished 触发 CLOSED
多人订阅同一路正常
```

## 代码检查

```bash
cd backend
mvn -q -DskipTests package
```

```bash
cd frontend
npm run build
```

```bash
cd client
go build -tags nolibopusfile -o robot-media-client ./cmd/robot-media-client
```

## 联调命令

```bash
docker ps --format '{{.Names}}'
```

```bash
ffprobe -v error -rtsp_transport tcp -select_streams v:0 -show_entries stream=codec_name,width,height -of json 'rtsp://192.168.124.204:8554/camera01'
```

```bash
cd backend
mvn spring-boot:run
```

```bash
cd client
go run -tags nolibopusfile ./cmd/robot-media-client
```

```bash
cd frontend
npm run serve
```

## 下一步开发顺序

1. 使用真实摄像头完成 RTSP 探测。
2. 安装 gstreamer-publisher 并验证 RTSP 到 LiveKit Publisher。
3. 完成前端真实视频订阅验收。
4. 完成抓拍 MinIO 入库验收。
5. 补集成测试。
6. 补生产部署脚本。
