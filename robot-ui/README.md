# Robot UI

`robot-ui` 是具身智能平台指挥中心的前端工程，基于 Vue 2 和 Element UI 开发，主要用于机器人状态展示、GIS/SLAM 地图、巡检任务、实时视频、语音对讲、告警和大屏数据展示。

## 技术栈

- Vue 2.6、Vue Router 3、Vuex 3
- Vue CLI 4、Webpack 4、Sass
- Element UI、ECharts
- Leaflet、OpenLayers
- LiveKit、WebRTC、FLV、HLS
- MQTT、WebSocket

## 环境要求

- Node.js：以 `package.json` 中的 `engines` 配置为最低要求，建议团队统一 Node.js 和 npm 版本。
- npm：项目使用 npm 管理依赖。
- 后端服务：启动前需要确认业务 API、控制 WebSocket、LiveKit 等服务可访问。

项目包含 `package-lock.json` 时，推荐使用 `npm ci` 保持依赖版本一致；需要更新依赖时再使用 `npm install`。

## 快速启动

```bash
cd robot-ui

# 按锁文件安装依赖
npm ci

# 启动开发服务
npm run dev
```

开发服务默认监听 `0.0.0.0:8080`，并自动打开浏览器。端口可临时指定：

```bash
npm run dev -- --port 8081
```

## 常用命令

```bash
# 启动开发环境
npm run dev

# 构建测试环境
npm run build:stage

# 构建生产环境
npm run build:prod

# 构建并在 9526 端口预览
npm run preview

# 代码检查
npm run lint

# 自动修复可修复的检查问题
npm run lint:fix
```

生产构建产物输出到 `dist/`，静态资源位于 `dist/static/`。

## 环境配置

环境文件位于项目根目录：

- `.env.development`：本地开发环境
- `.env.staging`：测试环境
- `.env.production`：生产环境

常用变量如下：

| 变量 | 作用 |
| --- | --- |
| `VUE_APP_TITLE` | 浏览器标题和系统名称 |
| `VUE_APP_BASE_API` | Axios 请求的基础路径 |
| `VUE_APP_BASE_ORIGIN` | 后端服务源地址，也用于部分地图和媒体接口 |
| `VUE_APP_WS_URL` | 机器人控制 WebSocket 地址，例如 `wss://host/ws/control` |
| `VUE_APP_VOICEWEBSOCKET_URL` | 语音服务 WebSocket 地址 |
| `VUE_APP_WEBSOCKET_URL` | 业务 WebSocket 地址 |
| `VUE_APP_WEBRTC` | WebRTC 服务地址 |
| `VUE_APP_YUNTAI_CONTROL` | 云台控制接口地址 |
| `VUE_APP_KEYCLOAK_URL` | Keycloak 对外地址 |
| `VUE_APP_KEYCLOAK_REALM` | Keycloak Realm，默认 `iam-auth` |
| `VUE_APP_KEYCLOAK_CLIENT_ID` | 大屏 Public SPA Client ID，默认 `bigscreen-web` |

开发环境示例：

```dotenv
VUE_APP_TITLE=具身智能平台指挥中心
VUE_APP_BASE_API=/dev-api
VUE_APP_BASE_ORIGIN=https://backend.example.com
VUE_APP_WS_URL=wss://backend.example.com/ws/control
VUE_APP_KEYCLOAK_URL=https://auth.example.com
VUE_APP_KEYCLOAK_REALM=iam-auth
VUE_APP_KEYCLOAK_CLIENT_ID=bigscreen-web
```

开发模式下，`/dev-api` 会通过 `vue.config.js` 代理到 `VUE_APP_BASE_ORIGIN`，并移除 `/dev-api` 前缀。

生产部署也可通过 `public/js/auth-config.js` 提供 Keycloak 运行时配置，无需将具体环境地址写进业务代码。
管理端需要为大屏注册 Public SPA 客户端并启用 Authorization Code + PKCE(S256)。
完整配置见[大屏统一登录认证对接指南](../docs/03-接口与协议/大屏BFF/大屏统一登录认证对接指南.md)。

修改环境文件后需要重新启动开发服务。环境文件中不要提交账号、Token、私钥等敏感信息。

## 目录结构

```text
robot-ui/
├── build/                  # 本地构建预览脚本
├── public/                 # 不经过 Webpack 处理的静态资源
├── src/
│   ├── api/                # HTTP 接口定义
│   ├── assets/             # 图片、字体、图标和全局样式
│   ├── components/         # 通用组件
│   ├── constants/          # 公共常量
│   ├── directive/          # Vue 自定义指令
│   ├── layout/             # 后台管理布局
│   ├── mixins/             # 公共混入
│   ├── plugins/            # Vue 插件和全局能力
│   ├── router/             # 路由配置
│   ├── services/           # 业务服务封装
│   ├── static/             # 视频和 WebRTC 相关脚本
│   ├── store/              # Vuex 状态管理
│   ├── utils/              # 请求、鉴权和工具函数
│   └── views/
│       └── bi/             # 指挥中心和大屏业务页面
├── .env.*                  # 各环境配置
├── package.json            # 依赖和 npm 命令
└── vue.config.js           # Vue CLI、代理和构建配置
```

`src/views/bi` 中的主要模块：

- `home/`：指挥中心首页
- `gis/`：GIS 地图和设备弹窗
- `patrol/panorama/`：巡检全景
- `patrol/monitor/`：实时监控、视频和对讲
- `patrol/business/`：巡检业务管理
- `patrol/slam/`：SLAM 地图及路径展示
- `staff/`：人员和装备相关页面

## 状态与实时通信

主要 Vuex 模块位于 `src/store/modules/`：

- `websocket-robot.js`：机器人控制连接、视频会话、音频对讲和 LiveKit 状态
- `websocket-extra-data.js`：设备、任务、告警、GIS/SLAM 地图和实时位置数据
- `websocket.js`：通用业务 WebSocket
- `voiceCall.js`：语音通话状态

开发实时功能时，应注意：

1. WebSocket 方法通常是异步流程，停止旧会话后再启动新会话时需要完整等待 Promise。
2. Vue 2 的 `$emit` 不会返回父组件监听函数的 Promise；需要等待父组件异步操作时，应传入回调或由父组件显式返回并转发 Promise。
3. 实时位置、设备状态等共享数据应通过 Vuex 更新，避免组件保存过期对象副本。

## 构建与部署

```bash
npm run build:prod
```

部署时将 `dist/` 发布到 Nginx 或其他静态文件服务，并配置：

- 单页应用路由回退到 `index.html`
- 业务 API 反向代理
- `/ws/control` 等 WebSocket 路径的 Upgrade/Connection 请求头
- `/livekit` 的 WebSocket 和 HTTP 代理
- HTTPS 站点使用浏览器信任、域名匹配且未过期的证书

Nginx 路由回退示例：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

生产环境通过 HTTPS 访问时，WebSocket 必须使用 `wss://`。如果浏览器提示 `ERR_CERT_AUTHORITY_INVALID`，说明服务端证书不受当前系统或浏览器信任，需要更换受信任的证书，或在内网环境正确安装并信任内部根证书。

## 常见问题

### API 请求失败

- 检查 `.env.development` 中的 `VUE_APP_BASE_ORIGIN`。
- 检查请求是否以 `VUE_APP_BASE_API` 配置的前缀发出。
- 检查 `vue.config.js` 中的代理目标和路径重写。
- 修改环境变量后重新启动开发服务。

### WebSocket 无法连接

- 确认 `VUE_APP_WS_URL` 的协议、域名、端口和路径正确。
- HTTPS 页面必须连接 `wss://`。
- 检查反向代理是否支持 WebSocket Upgrade。
- 检查证书是否受浏览器信任，证书域名是否与访问地址一致。

### 视频或对讲无声音

- 检查浏览器摄像头、麦克风和自动播放权限。
- 确认 LiveKit、控制 WebSocket 和媒体服务均已连接。
- 检查浏览器控制台中的 ICE、WebRTC 和证书错误。

### 开发端口被占用

```bash
npm run dev -- --port 8081
```

### 修改代码后页面没有更新

- 确认开发服务仍在运行。
- 环境变量变更后重启开发服务。
- 必要时清理 `node_modules/.cache` 后重新启动。

## 开发约定

- 优先复用现有组件、Vuex 模块和业务服务。
- 页面共享状态统一放入 Vuex，组件内部仅保存局部交互状态。
- 新增接口放在 `src/api/` 或现有服务模块中，不在组件里重复拼接请求逻辑。
- 新增通用样式前先检查 `src/assets/styles/` 中是否已有定义。
- 提交前至少执行一次 `npm run lint`；涉及构建配置、路由或依赖时，再执行 `npm run build:prod`。
