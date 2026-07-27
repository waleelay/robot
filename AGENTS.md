# AGENTS.md

## 适用范围

本文件适用于整个仓库。若子目录中存在更具体的 `AGENTS.md` 或
`AGENTS.override.md`，则在该子目录范围内优先遵循更具体的说明。

## 项目定位

本仓库是具身智能装备集成管理平台的媒体与控制相关单仓库，包含 Java、
Go、Python 和 Vue 项目。开始修改前先阅读根目录 `README.md`；涉及具体
业务流程时，再阅读 `docs/` 中与任务直接相关的设计或接口文档，避免把
归档方案当成当前实现。

常用文档入口：

- 实时视频链路：`docs/实时视频接口流程.md`
- 前端、控制服务与媒体服务接口：`docs/前端控制与后端Java接口文档.md`
- 客户端事件与载荷：`docs/客户端上报事件与载荷.md`
- 文件上传、存储与播放：`docs/通用文件上传存储与播放设计.md`
- 本地构建与联调：`docs/开发流程.md`

## 模块与架构边界

- `backend/`：媒体服务，负责视频会话、LiveKit Token/Room、媒体文件及
  媒体状态。
- `control-service/`：控制服务，负责 `/api/control/**`、`/ws/control`、
  机器人在线状态以及 MQTT 指令和状态桥接。
- `bigscreen-bff/`：面向大屏前端的 REST/WebSocket 代理与聚合层，不承载
  媒体流，也不应复制 Control 或 Media 的核心业务。
- `client/`：机器人侧 Go 客户端。
- `python-client/`：机器人侧 Python 客户端，功能边界应与 Go 客户端保持
  一致。
- `frontend/`：实时视频调试前端。
- `robot-ui/`：指挥中心前端。

修改跨服务协议时，应同时检查生产者、消费者、DTO/模型、WebSocket 或
MQTT 事件载荷及相关文档。不要在没有明确理由的情况下跨模块复制业务逻辑
或引入第二套并行协议。

## 工作约定

- 保留工作区中与当前任务无关的已有修改，不覆盖、不回退、不顺手格式化
  无关文件。
- 优先在现有结构中完成最小闭环修改；新增依赖、服务或协议前先说明必要性。
- 不提交密码、Token、私钥、证书、真实设备凭据或其他敏感信息。
- 环境相关值通过配置文件占位或环境变量表达，不把本机 IP、账号或临时调试
  值写成生产默认值。
- 代码注释和项目文档使用简体中文；标识符、协议字段和外部 API 名称保持其
  原有语言与拼写。
- 分支默认使用 `codex/` 前缀，提交信息使用简体中文，一个提交对应一个可
  验证的闭环能力。
- 生成物、依赖目录和大型二进制文件不得因普通代码修改被意外加入版本控制。

## 构建与测试

根据修改范围执行最小充分验证。根目录快速构建检查：

```bash
sh scripts/dev-check.sh
```

分模块常用命令：

```bash
# Java 服务
(cd backend && mvn test)
(cd control-service && mvn test)
(cd bigscreen-bff && mvn test)

# Go 客户端
(cd client && go test -tags nolibopusfile ./...)
(cd client && go build -tags nolibopusfile -o robot-media-client ./cmd/robot-media-client)

# Python 客户端
(cd python-client && python -m unittest discover -s tests)

# 调试前端
(cd frontend && npm run build)

# 指挥中心前端
(cd robot-ui && npm run lint)
(cd robot-ui && npm run build:prod)
```

只运行与改动有关的命令；涉及共享协议或跨服务流程时，应验证所有受影响
模块。测试依赖 Docker 中间件、真实设备、摄像头、LiveKit、MinIO、MQTT
或外部管理服务而无法执行时，在交付说明中明确列出未验证项和原因，不要把
未执行描述为已通过。

## 文档同步

接口、事件载荷、环境变量、启动方式、模块职责或架构边界发生变化时，同步
更新 `README.md` 及 `docs/` 中对应文档。优先链接权威文档，不在多个文件
中复制容易漂移的大段说明。

## 代码审查重点

- 检查跨服务接口和字段含义是否保持兼容。
- 检查会话、Track、MQTT ACK/status 和 WebSocket 事件的生命周期是否闭环。
- 检查异常、超时、断线重连、重复消息和幂等场景。
- 检查资源是否正确释放，包括推流进程、LiveKit 会话、文件句柄和后台任务。
- 检查日志是否包含必要上下文且不泄露凭据。
- 检查测试是否覆盖本次修改的正常路径和关键失败路径。
