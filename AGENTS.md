# AGENTS.md

## 适用范围

本文件适用于整个仓库。若子目录中存在更具体的 `AGENTS.md` 或
`AGENTS.override.md`，则在该子目录范围内优先遵循更具体的说明。

## 项目定位

本仓库是具身智能装备集成管理平台的媒体与控制相关单仓库，包含 Java、
Go 和 Vue 项目。开始修改前先阅读根目录 `README.md`；涉及具体
业务流程时，再阅读 `docs/` 中与任务直接相关的设计或接口文档，避免把
归档方案当成当前实现。

常用文档入口：

- 文档总索引：`docs/README.md`
- 实时视频链路：`docs/03-接口与协议/实时视频/实时视频接口与协议文档.md`
- Java 服务接口总览：`docs/03-接口与协议/Java服务接口总览.md`
- 控制服务接口：`docs/03-接口与协议/统一控制/控制服务接口文档.md`
- 客户端事件与载荷：`docs/03-接口与协议/客户端事件/客户端事件与载荷协议文档.md`
- 文件上传、存储与播放：`docs/02-设计/文件服务/通用文件上传存储与播放设计说明书.md`
- 实时视频测试与联调：`docs/04-测试与验收/测试方案/实时视频模块测试方案.md`

## 模块与架构边界

- `media-service/`：媒体服务，负责视频会话、LiveKit Token/Room、媒体文件及
  媒体状态。
- `control-service/`：控制服务，负责 `/api/control/**`、`/ws/control`、
  机器人在线状态以及 MQTT 指令和状态桥接。
- `bigscreen-bff/`：面向大屏前端的 REST/WebSocket 代理与聚合层，不承载
  媒体流，也不应复制 Control 或 Media 的核心业务。
- `fixed-camera-gateway/`：现场固定摄像头 Gateway，负责 RTSP、LiveKit、MQTT
  与健康探测。
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
- 项目自有日志的说明文字、展示标签、代码注释和项目文档使用简体中文。
  标识符、协议字段/值、事件与错误编码、外部 API 名称保持原有拼写；保留原始
  异常类型和堆栈，不伪造第三方诊断。日志翻译须保留级别、占位符、参数与脱敏边界。
- 后续开发以 `local` 分支为基线；`main` 只保留正式服务和必要工程资源，并按需接收已验证的正式改动。需要新建任务分支时使用 `codex/` 前缀。
- 提交信息使用简体中文，一个提交对应一个可
  验证的闭环能力。Git 提交格式、Body 长描述模板、类型和 scope 约定详见根目录
  [`CONTRIBUTING.md`](CONTRIBUTING.md)。
- 生成物、依赖目录和大型二进制文件不得因普通代码修改被意外加入版本控制。

## Alibaba 与 OpenAPI 规范

- 新增开发和存量整改遵循
  [Alibaba 与 OpenAPI 开发规范](docs/02-设计/工程规范/Alibaba与OpenAPI开发规范.md)，
  修改前读取其中适用规则、协议兼容性边界和实际检查状态。
- Alibaba Java 规范覆盖 Java 生产及测试代码；OpenAPI 覆盖 HTTP 契约。
  WebSocket/MQTT 继续遵循专项协议，不以 OpenAPI 检查替代事件生命周期验证。
- 新增代码不得引入违反适用强制规则的问题；历史问题按基线分批整改，
  不批量忽略、不虚构作者信息、不为统一格式改变存量接口行为。
- 首批检查命令及覆盖范围见 [quality/README.md](quality/README.md)。Java 变更运行
  `sh scripts/quality-check.sh java --base <明确基线>`，HTTP 接口变更运行
  `sh scripts/quality-check.sh openapi`；分支审查使用实际比较目标，不默认用 HEAD。
  未覆盖规则继续人工审查，不把执行失败或覆盖不完整报告为全部合规。
- 注释按规范第 2.1 节统一：枚举值解释业务含义，record 逐项说明组成字段，
  公开方法补参数/返回/异常；关键分支说明状态前提、锁序和补偿原因。
  不用方法名复述、空标签或过期参数充当有效说明。
- 规则和工具的已验证范围、剩余工作见
  [规范整改执行记录](docs/04-测试与验收/执行记录/规范整改执行记录-20260929.md)。

## 构建与测试

根据修改范围执行最小充分验证。根目录快速构建检查：

```bash
sh scripts/dev-check.sh
```

分模块常用命令：

```bash
# Java 服务
(cd media-service && mvn test)
(cd control-service && mvn test)
(cd bigscreen-bff && mvn test)

# 固定摄像头 Gateway
(cd fixed-camera-gateway && go test ./...)
(cd fixed-camera-gateway && go build -o fixed-camera-gateway ./cmd/fixed-camera-gateway)

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
