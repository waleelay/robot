# 现场 App 视频呼叫并入大屏后端设计说明

> 状态：已按本说明落地首版实现（Media Token API / Control 状态机 / BFF+Nginx 桥接 / 大屏复用 `/ws/bigscreen` / App `field.call.*`）。

## 1. 目标

不再单独部署 Node 信令（`tools/field-call/signaling`）。现场 App 与指挥中心的呼叫信令并入现有 Java 服务：

| 角色 | 服务 |
|------|------|
| 鉴权入口 / WS 桥 | Bigscreen BFF |
| 来电状态机 | Control Service |
| LiveKit Token | Media Service |
| 媒体 | 现网 LiveKit |

## 2. 连接拓扑

```text
App  --WSS /ws/field-call?access_token=JWT--> Nginx --> BFF --> Control /ws/field-call
大屏 --WSS /ws/bigscreen --------------------> Nginx --> BFF --> Control /ws/control
Control 接听后 HTTP --> Media POST /internal/media/field-calls
App / 大屏 <--WebRTC--> LiveKit
```

## 3. 协议摘要

### App → Control（`/ws/field-call`）

- `field.call.invite` / `field.call.cancel` / `field.call.hangup`
- 回包：`field.call.invite.ok` / `field.call.accepted`（含 token）/ `rejected` / `timeout` / `ended` / `busy` / `error`

### 大屏 ↔ Control（经 `/ws/bigscreen`）

- 推送：`video.field.call.incoming` / `video.field.call.status`
- 请求：`video.field.call.accept|reject|hangup|query`
- 接听回包：`video.field.call.accepted`，payload `{ call, session:{livekitUrl,roomName,token} }`

## 4. 关键代码位置

- Media：`backend/.../fieldcall/FieldCallMediaService.java`、`FieldCallController`
- Control：`control-service/.../call/FieldCallService.java`、`ws/FieldCallWebSocketHandler.java`
- BFF：`CenterServiceProperties.websocketFieldCallUrl`、`/ws/field-call` 注册
- Nginx：`location /ws/field-call`
- 大屏：`robot-ui/src/store/modules/fieldCall.js`（不再连独立 Node）
- App：`lib/services/field_call_signaling.dart`、`auth_session.dart`

## 5. App 打包示例

```powershell
flutter build apk --release `
  --dart-define=FIELD_CALL_WS=wss://211.137.109.150:4443/ws/field-call `
  --dart-define=FIELD_CALL_ACCESS_TOKEN=<平台JWT> `
  --dart-define=FIELD_CALL_LIVEKIT=wss://211.137.109.150:4443/livekit
```

后续应将 Mock 登录替换为真实 Keycloak / 平台登录，并把 JWT 写入 `AuthSession`。

## 6. 运维注意

- 删除 / 停用独立 Node:6090 与 Demo LiveKit:6880。
- 大屏构建无需 `VUE_APP_FIELD_CALL_WS`。
- 防火墙仍需放行现网 LiveKit UDP 端口。
