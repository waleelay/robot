# Java 服务接口总览

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 当前代码基线 |
| 基线日期 | 2026-08-23 |
| 适用模块 | `bigscreen-bff`、`control-service`、`backend` |

## 1. 文档定位

本文档只负责三个 Java 服务的入口导航和边界说明，不重复维护全部字段。原《前端、Control Service 与 Media Service 接口文档》同时混入三项服务、MQTT、前端调用和数据模型，已迁入 `docs/归档/历史接口总册/`，不再作为当前接口依据。

当前权威接口入口如下：

| 调用边界 | 权威文档 |
| --- | --- |
| 通用鉴权、用户上下文、时间与错误响应 | [Java 服务接口通用约定](公共约定/Java服务接口通用约定.md) |
| 浏览器/大屏前端 -> Bigscreen BFF | [大屏 BFF 接口文档](大屏BFF/大屏BFF接口文档.md) |
| 前端/BFF -> Control Service | [控制服务接口文档](统一控制/控制服务接口文档.md) |
| Control Service/机器人 -> Media Service | [媒体服务接口文档](媒体服务/媒体服务接口文档.md) |
| 实时视频 REST、WebSocket、MQTT 与状态机 | [实时视频接口与协议文档](实时视频/实时视频接口与协议文档.md) |
| 通用文件字段、上传、下载与播放 | [通用文件服务接口文档](文件服务/通用文件服务接口文档.md) |
| MQTT 客户端状态、设备控制与主动呼叫载荷 | [客户端事件与载荷协议文档](客户端事件/客户端事件与载荷协议文档.md) |

## 2. 服务调用方向

```text
浏览器 / robot-ui
  -> Bigscreen BFF :8090（JWT 验证、聚合、代理、WebSocket 桥接）
  -> Control Service :8082（控制编排、机器人状态、MQTT）
  -> Media Service :8088（视频会话、LiveKit、文件、TTS）

Control Service -> Management Service（设备档案、能力、固定摄像头）
Control Service -> Media Service /internal/media/**
Control Service <-> EMQX <-> 机器人客户端 / 固定摄像头 Gateway
浏览器 <-> LiveKit（媒体流不经过 BFF 或 Control）
```

Control 的 `/api/control/statistics/mileage` 由边缘状态里程读数计算并持久化增量，供 BFF 统计使用；它不是 Management 的历史报表接口。

## 3. 对外与内部接口边界

| 服务 | 对外入口 | 内部入口 | 不承担的职责 |
| --- | --- | --- | --- |
| Bigscreen BFF | `/api/**`、`/ws/control`、`/ws/media`、`/ws/bigscreen` | 调用 Management、Control、Media | 不承载媒体流，不复制下游核心业务 |
| Control Service | `/api/control/**`、`/ws/control`、兼容 `/ws/media` | 调用 `/internal/media/**`、Management；收发 MQTT | 不保存媒体文件，不签发 LiveKit Token |
| Media Service | `/api/media/files/**`、`/api/media/tts/**`、`/ws/media` | `/internal/media/video-sessions/**`、`/internal/media/files/**` | 不维护机器人业务档案，不负责 MQTT 下发 |

## 4. 维护规则

1. 字段和状态变更先修改生产者/消费者代码，再同步对应专项接口文档。
2. 同一事实只保留一个权威位置；服务接口文档链接实时视频、文件等专项协议，不复制整章。
3. 面向外部调用方的完整示例放在接口文档或对接指南；模块代码结构、启动和配置放在模块 `README.md`。
4. 已废止接口和历史设计进入 `docs/归档/`，不得与当前接口并列展示。
