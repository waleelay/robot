# 实时视频调试前端

`frontend/` 是基于 Vue 2 和 Element UI 的实时视频调试台，用于创建和管理视频会话、订阅 LiveKit Track、视频墙调试、抓拍、录像及语音对讲验证。

## 代码结构

```text
src/
├── api/        Media/Control HTTP 接口封装
├── styles/     页面样式
├── App.vue     调试台主页面和交互流程
└── main.js     Vue 应用入口
```

## 环境要求

- Node.js 18+
- npm 9+
- 可访问的 Bigscreen BFF、Control Service、Media Service 和 LiveKit

## 配置

| 环境变量 | 说明 |
| --- | --- |
| `VUE_APP_API_BASE` | HTTP API 代理目标 |
| `VUE_APP_WS_BASE` | `/ws/control` 的 WebSocket 代理目标 |
| `VUE_APP_WS_URL` | 显式指定业务 WebSocket 地址 |

本地开发代理定义在 `vue.config.js`。不要将本机 IP 或真实凭据写成生产默认值。

## 启动与构建

```bash
cd frontend
npm install
npm run serve
npm test
npm run build
```

开发服务默认监听 `0.0.0.0:8090`，构建产物输出到 `dist/`。

## 关键流程

```text
创建或复用视频会话
  -> 获取 LiveKit 地址和 viewer token
  -> 连接 Room
  -> 订阅 video Track
  -> 通过 viewer heartbeat 维持观看关系
  -> 停止观看并释放本地 Track
```

实现和联调时参考：

- [实时视频接口与协议文档](../docs/03-接口与协议/实时视频/实时视频接口与协议文档.md)
- [实时视频普通播放时序图](../docs/02-设计/实时视频/实时视频普通播放时序图.md)
- [实时视频模块验收用例](../docs/04-测试与验收/验收用例/实时视频模块验收用例.md)

### 控制画像与设备类型

调试台只从 `control-profile.devices[]` 取得控制设备定义；画像尚未加载或为空时，
不从机器人状态推送生成控制入口。推送按平台 `deviceId` 精确匹配，仅补充
`status`（兼容旧字段 `runtimeStatus`）和 `onlineStatus`，不覆盖设备类型、动作、
作用域和控制参数。驱动 ID 或设备类型不参与身份兜底匹配。
云台与音频等状态按画像类型分发，客户端省略或误报 `deviceType` 不会改变控件类型。
异步画像响应使用请求的机器人 ID 同步音频状态，避免切换机器人后串写缓存。

调试台仍消费服务端保留的合并字段 `devices[]`，保留既有本地操作缓存；本次调整
限定于画像权威来源与身份关联，不代表已经具备指挥中心 `robot-ui` 的完整运行态
版本防护、断线清理和本地确认失效机制。正式控制的运行态约定见
[控制服务接口文档](../docs/03-接口与协议/统一控制/控制服务接口文档.md)。

`npm test` 直接执行组件方法并隔离网络和本地存储，覆盖类型冲突、未知与别名 ID、
同类型多设备、旧运行字段及异步切换机器人；不能替代真实设备联调。

## 开发约定

- HTTP 请求统一封装在 `src/api/`。
- 视频、音频 Track 在会话停止、页面销毁和异常断开时必须释放。
- HTTPS 页面只能连接 `wss://` WebSocket 和 LiveKit 信令地址。
- 修改视频会话、对讲或 WebSocket 流程后至少执行一次 `npm run build`。
