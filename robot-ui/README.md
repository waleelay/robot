# Robot UI

`robot-ui` 是具身智能平台指挥中心的前端工程，基于 Vue 2 和 Element UI 开发，主要用于机器人状态展示、GIS/SLAM 地图、巡检任务、实时视频、语音对讲、告警和大屏数据展示。

## 装备弹窗数据

装备弹窗 `Robot1.vue` 直接使用 Overview 初始化的共享装备状态，并由既有 `robot.state` 事件更新
电量、速度、控制模式和在线状态，不等待额外详情请求。普通机器人仅在
`mountedDeviceCount == null` 时调用
`/api/bigscreen/panorama/devices/{robotId}/mounted-device-count` 补充上装设备数量；Overview 已有
数量或选中固定摄像头时不请求。补充查询失败时数量显示 `-`，不增加点击操作；重新打开或切换机器人时
自然重新查询。电量、速度、控制模式及在线状态复用 `websocketRobot` 共享状态，按
`runtimeUpdatedAt/statusChangedAt` 合并；弹窗不再单独订阅事件或维护运行态缓存。
请求在途去重，切换、关闭或销毁时取消并清空；失败时原标题提供重试，未知值显示 `-`，离线速度和
模式不标作当前值。查询成功后本次打开期间不再查详情；重连不清空档案，也不重查详情，运行态恢复
复用现有 WebSocket 和重连后的 Overview 刷新。再次打开或切换装备才重新查询，首次失败可重试。
加载入口统一为 `mountedDeviceCountTarget` 的立即监听，不再叠加多个字段监听。
状态新旧只在共享合并函数中判断；全局档案不保留旧详情数量，弹窗数量稳定性由本次详情快照保证。
Overview 继续服务地图、装备列表及共享媒体/控制状态，本次不删减它的响应字段；任务列表、任务路径、
视频准入与会话仍复用原共享链路，不通过详情另建一套任务或视频状态。
该链路回归命令：`node --test test/robot-popup-state.test.mjs`。

## 技术栈

- Vue 2.6、Vue Router 3、Vuex 3
- Vue CLI 4、Webpack 4、Sass
- Element UI、ECharts
- Leaflet、OpenLayers
- LiveKit、WebRTC、FLV、HLS
- MQTT、WebSocket

实时监控手动媒体的图片预览和下载使用 `/api/control/files/{fileId}/download-url` 返回的签名地址，
视频仍使用 `play-url` 的 HLS 地址；页面不再把大文件正文先下载为 Blob 后再展示。图片签名地址按
`fileId` 复用，距离 `expiresAt` 不足 60 秒时重新签发，注销、切换用户或删除文件时清除缓存。

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
| `VUE_APP_BASE_API` | Axios 请求的基础路径，生产环境使用 `/api-gw` |
| `VUE_APP_BASE_ORIGIN` | 后端服务源地址，也用于部分地图和媒体接口 |
| `VUE_APP_WS_URL` | 大屏 WebSocket 地址，例如 `wss://host/ws/bigscreen`；不配置时使用当前页面同源地址 |
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
VUE_APP_WS_URL=wss://backend.example.com/ws/bigscreen
VUE_APP_KEYCLOAK_URL=https://auth.example.com
VUE_APP_KEYCLOAK_REALM=iam-auth
VUE_APP_KEYCLOAK_CLIENT_ID=bigscreen-web
```

开发模式下，`/dev-api` 会通过 `vue.config.js` 代理到 `VUE_APP_BASE_ORIGIN`，并移除 `/dev-api` 前缀。

生产部署也可通过 `public/js/auth-config.js` 提供 Keycloak 运行时配置，无需将具体环境地址写进业务代码。
管理端需要为大屏注册 Public SPA 客户端并启用 Authorization Code + PKCE(S256)。
完整配置见[大屏统一登录认证对接指南](../docs/03-接口与协议/大屏BFF/大屏统一登录认证对接指南.md)。

授权码交换成功后，前端使用 History API 删除地址栏中的 `code/state/session_state/iss`，同时
保留业务查询参数、pathname 和 hash 路由且不刷新页面；为覆盖部分浏览器中 Keycloak 适配器的延迟回写，
会在当前、下一事件循环、500 ms、2 秒和 5 秒复查并只删除这些一次性协议参数。交换失败时不会提前清理回调参数。
全景接口返回 `dataQuality.tasks.degraded=true` 时，页面保留已成功加载的数据；统计页按既有统计
字段展示，不弹出任务数据降级提示。

菜单权限验证由 `public/js/auth-config.js` 的 `permissionEnabled` 控制，默认开启。
设为 `false` 时不请求权限接口、不拦截路由，并展示全部一级/二级菜单。
开启后，登录完成后前端请求 BFF 的 `GET /api/bigscreen/access-control/me`，由 BFF 透传当前 JWT 到
EIOP `GET /api/v1/management/access-control/me`，并使用返回的 `permissions` 过滤大屏一级菜单、
巡逻二级菜单和路由访问。菜单权限码以 EIOP 权限目录为准；
BFF 继续向管理端透传当前用户 JWT，菜单权限不替代管理端的数据权限校验。

生产环境的 REST 访问 `/api-gw/api/bigscreen/**`，由大屏 Nginx 转发到管理端
API Gateway；WebSocket 访问同源 `/ws/bigscreen`，由大屏 Nginx 直接转发到 BFF。

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

全景数据读取遵循“摘要先到、详情按需”的边界：首屏只读取 `overview`，默认 SLAM 地图再读取一次当前地图
场景和任务路径；用户切换 SLAM 地图时只读取新地图资源；用户打开任务视频时才读取任务完整详情。单个页面
同一时间只保留一个首屏读取，短暂失败仅以 500～799 ms 随机延迟重试一次；详情读取失败时仍使用已加载的
任务摘要打开视频，不整页刷新或中断已有视频。

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
4. 固定摄像头健康变化通过 `panorama.fixed-camera.statuses.changed` 只更新当前授权快照中已有摄像头的在线与播放准入状态，不刷新 Overview；只有收到 `bigscreen.authorization.changed` 确认权限集合变化时才按 fail-closed 方式清理无权资源并重取 Overview。`4001` 表示登录凭证自然到期并静默重连，`4003` 才表示授权快照已经失效。
5. 首页与实时监控的底图、工具栏及任务筛选统一读取 `websocketExtraData.globalMapId`。续期/重连刷新保留仍在新地图列表中的选择（含用户手动选择的 GIS）；仅首屏或所选地图已移除时按 GPS→首张 SLAM→GIS 选择默认地图。退出登录清空选择，不跨账号保存。
6. 地图资源和路径只加载当前选择，刷新期间用户切图以最新选择为准。同一快照同一地图的在途请求合并；快照替换、失权或退出后丢弃旧响应，切到其他图后旧图响应也不写回。普通 Overview 失败保留当前页面，明确权限变化仍先清空。

地图、弹窗与实时监控回归：`node --test test/panorama-map-state.test.mjs test/robot-popup-state.test.mjs test/realtime-monitor-camera-recovery.test.mjs`。

## 构建与部署

```bash
npm run build:prod
```

部署时将 `dist/` 发布到 Nginx 或其他静态文件服务，并配置：

- 单页应用路由回退到 `index.html`
- 业务 API 反向代理
- `/api-gw/` 的 API Gateway 反向代理及 WebSocket Upgrade/Connection 请求头
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

- 云台控制的共享 mixin 统一位于
  `src/views/bi/patrol/monitor/second/components/ptz-control-mixin.js`；组件文件保持
  `Yuntai.vue`。不得再创建仅靠大小写区分的 `yuntai.js`，也不得增加旧路径兼容转发。
- 文件名在 macOS 和 Linux 上必须具有一致解析结果；新增或重命名组件后执行生产构建并处理
  CaseSensitivePaths 告警。

- 优先复用现有组件、Vuex 模块和业务服务。
- 页面共享状态统一放入 Vuex，组件内部仅保存局部交互状态。
- 新增接口放在 `src/api/` 或现有服务模块中，不在组件里重复拼接请求逻辑。
- 新增通用样式前先检查 `src/assets/styles/` 中是否已有定义。
- 提交前至少执行一次 `npm run lint`；涉及构建配置、路由或依赖时，再执行 `npm run build:prod`。
