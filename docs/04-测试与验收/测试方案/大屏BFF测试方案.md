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
- 任务计划、流程定义和执行记录的业务白名单代理；当前不测试未实现的快捷任务专用接口。
- WebSocket 桥接、事件转换、重连和重复事件。
- Nginx 反向代理、跨域和 hop-by-hop Header 过滤。

## 2. 测试策略

1. 对每个聚合字段核对字段来源映射文档。
2. 下游无数据时验证返回 `null`、空集合或明确状态，不生成生产假数据。
3. 分别模拟管理、控制和媒体服务超时及错误。
4. 验证 REST 快照与 WebSocket 增量最终一致。
5. 通过直连 BFF 和 Nginx 两种入口执行接口测试。
6. 对任务来源分别模拟详情 404、列表连接超时、读取超时、并发许可耗尽和执行器队列饱和；
   核对 `dataQuality.tasks`、部分数据保留、任务总数/完成率空值及中文降级提示。
7. OIDC 登录成功后核对地址栏、浏览器历史、Referer 和 Nginx 日志不含授权码；交换失败时
   回调参数仍保留，业务查询参数和 hash 路由不丢失。

## 3. 构建与接口检查

```bash
(cd bigscreen-bff && mvn test)
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" http://127.0.0.1:8090/api/bigscreen/panorama/overview
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" http://127.0.0.1:8090/api/bigscreen/panorama/tasks
curl -sS -H "Authorization: Bearer $ACCESS_TOKEN" http://127.0.0.1:8090/api/bigscreen/panorama/alarms
```

`ACCESS_TOKEN` 必须由测试环境的真实 Issuer 签发，并满足 BFF 的 issuer、算法和 `azp/aud` 校验；不得用手工用户 Header 代替认证测试。

通过 Nginx 联调时使用实际测试环境地址，不在文档中固化本机 IP。

## 4. 退出条件

- 接口字段与数据来源映射一致。
- 下游异常不会转换为看似真实的成功数据。
- WebSocket 重连后能够恢复快照与增量。
- 业务白名单代理的允许路径和拒绝路径均有执行结论。
- 任务降级不会在全景页、统计页或 PDF 中表现为真实的零值；401/403 不被降级吞掉。
- 所有参与 JWT、授权快照和目录租约的节点执行 `MAX_OFFSET_SECONDS=1 sh scripts/check-time-sync.sh` 通过。
