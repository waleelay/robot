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

## 5. App 认证与打包

App 与大屏共用 Keycloak Realm 和平台账号，但使用独立的 Public Native
Client `field-app`，通过 Authorization Code + PKCE(S256) 登录。Android 回调
URI 固定为 `com.eiop.eiopmobile:/oauthredirect`，与手机 IP 无关。App 不保存
用户密码，也不使用 Direct Access Grants。

```powershell
flutter build apk --release `
  --dart-define=FIELD_CALL_WS=wss://211.137.109.150:4443/ws/field-call `
  --dart-define=FIELD_CALL_LIVEKIT=wss://211.137.109.150:4443/livekit `
  --dart-define=KEYCLOAK_URL=https://211.137.109.150:18443 `
  --dart-define=KEYCLOAK_REALM=iam-auth `
  --dart-define=KEYCLOAK_CLIENT_ID=field-app `
  --dart-define=KEYCLOAK_REDIRECT_URI=com.eiop.eiopmobile:/oauthredirect
```

Keycloak 需给现场账号授予 `FIELD_OPERATOR` 角色；BFF 只允许
`field-app` Token 连接 `/ws/field-call`，不允许访问大屏 REST 和
`/ws/bigscreen`。

## 6. 运维注意

- 删除 / 停用独立 Node:6090 与 Demo LiveKit:6880。
- 大屏构建无需 `VUE_APP_FIELD_CALL_WS`。
- BFF 配置 `FIELD_CALL_AUTH_CLIENT_ID=field-app`。
- Keycloak 和 BFF/LiveKit 入口的 HTTPS 证书必须被手机系统信任。
- 防火墙仍需放行现网 LiveKit UDP 端口。
