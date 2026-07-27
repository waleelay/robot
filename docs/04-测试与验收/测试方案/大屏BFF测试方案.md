# 大屏 BFF 测试方案

| 文档属性 | 内容 |
| --- | --- |
| 文档状态 | 待评审 |
| 测试对象 | Bigscreen BFF、管理服务、Control Service、Media Service、指挥中心前端 |
| 关联设计 | `../../02-设计/大屏BFF/` |
| 关联协议 | `../../03-接口与协议/大屏BFF/` |

## 1. 测试范围

- 全景总览、设备详情、任务、告警和统计接口。
- 字段来源、空值、缺失数据和枚举转换。
- 下游超时、错误响应和不可用场景。
- 快捷任务查询、创建、状态查询和取消。
- WebSocket 桥接、事件转换、重连和重复事件。
- Nginx 反向代理、跨域和 hop-by-hop Header 过滤。

## 2. 测试策略

1. 对每个聚合字段核对字段来源映射文档。
2. 下游无数据时验证返回 `null`、空集合或明确状态，不生成生产假数据。
3. 分别模拟管理、控制和媒体服务超时及错误。
4. 验证 REST 快照与 WebSocket 增量最终一致。
5. 通过直连 BFF 和 Nginx 两种入口执行接口测试。

## 3. 构建与接口检查

```bash
(cd bigscreen-bff && mvn test)
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/overview
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/tasks
curl -sS http://127.0.0.1:8090/api/bigscreen/panorama/alarms
```

通过 Nginx 联调时使用实际测试环境地址，不在文档中固化本机 IP。

## 4. 退出条件

- 接口字段与数据来源映射一致。
- 下游异常不会转换为看似真实的成功数据。
- WebSocket 重连后能够恢复快照与增量。
- 快捷任务验收用例全部有执行结论。

快捷任务验收步骤见[大屏快捷任务验收用例](../验收用例/大屏快捷任务验收用例.md)。
