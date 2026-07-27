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

## 开发约定

- HTTP 请求统一封装在 `src/api/`。
- 视频、音频 Track 在会话停止、页面销毁和异常断开时必须释放。
- HTTPS 页面只能连接 `wss://` WebSocket 和 LiveKit 信令地址。
- 修改视频会话、对讲或 WebSocket 流程后至少执行一次 `npm run build`。
