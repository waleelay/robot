# 大屏数据统计接口文档

## 1. 约定

大屏前端统一访问 Bigscreen BFF：

```text
/api/bigscreen/statistics/**
```

时间格式：

```text
yyyy-MM-dd HH:mm:ss
```

设备类型：

| 值 | 含义 |
|---|---|
| `all` | 全部 |
| `UAV` | 无人机 |
| `ROBOT_DOG` | 机器狗 |
| `UGV` | 无人车 |
| `HUMANOID_ROBOT` | 机器人 |

时间范围：

| 值 | 含义 |
|---|---|
| `all` | 全部 |
| `today` | 今日 |
| `week` | 本周/最近 7 天 |
| `month` | 本月 |
| `custom` | 自定义 |

## 2. 数据统计总览

```http
GET /api/bigscreen/statistics/overview
```

当前第一版为 BFF mock 数据，但会按筛选条件返回不同结果：

- `range=all|today|week|month` 会影响 KPI 数量、对比标签、趋势图点位、任务完成率和分析文案。
- `deviceType=all|UAV|ROBOT_DOG|UGV|HUMANOID_ROBOT` 会影响 KPI 数量、运行时长分布、告警数量和趋势图数值。
- `range=custom` 暂按 `month` 量级展示，保留 `startTime/endTime` 回显能力，后续接真实数据时再按时间跨度聚合。

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `range` | string | 否 | `all`、`today`、`week`、`month`、`custom`，默认 `month` |
| `startTime` | string | 否 | 自定义开始时间，`range=custom` 时传 |
| `endTime` | string | 否 | 自定义结束时间，`range=custom` 时传 |
| `deviceType` | string | 否 | 设备类型，默认 `all` |
| `areaId` | string | 否 | 区域 ID |

### 响应示例

```json
{
  "serverTime": "2026-06-15 15:30:00",
  "range": {
    "type": "month",
    "startTime": "2026-06-01 00:00:00",
    "endTime": "2026-06-15 23:59:59"
  },
  "filters": {
    "deviceType": "all",
    "areaId": null
  },
  "kpis": {
    "taskTotal": {
      "value": 288,
      "compareRate": -5
    }
  },
  "equipmentRuntime": {
    "onlineRate": 98,
    "taskCompletionRate": 100,
    "unit": "小时",
    "items": [
      {
        "deviceType": "UGV",
        "deviceTypeName": "无人车",
        "runningHours": 88,
        "faultHours": 51,
        "offlineHours": 37
      }
    ]
  },
  "aiAlarmAnalysis": {
    "alarmTypeRanking": [
      {
        "type": "FIGHT",
        "name": "打架斗殴",
        "count": 80,
        "percent": 28.0
      }
    ],
    "handleMethodRanking": [
      {
        "method": "VOICE_BROADCAST",
        "name": "语音播报",
        "count": 96
      }
    ]
  },
  "alarmAreaRanking": [
    {
      "areaId": "area-001",
      "areaName": "2监区8号楼",
      "count": 52,
      "percent": 100
    }
  ],
  "alarmTrend": {
    "unit": "次",
    "points": [
      {
        "date": "2026-06-15",
        "label": "6.15",
        "count": 44
      }
    ]
  },
  "taskCompletion": {
    "items": [
      {
        "status": "COMPLETED",
        "name": "已完成",
        "count": 196,
        "percent": 98
      }
    ],
    "insight": "本月对比上月任务处置时长缩短10%，系统响应速度提升"
  }
}
```

## 3. 一键生成报告

当前版本同步生成并返回文件流。

```http
POST /api/bigscreen/statistics/reports/export
Content-Type: application/json
Accept: application/pdf
```

### 请求体

```json
{
  "modules": [
    "equipmentRuntime",
    "aiAlarmAnalysis",
    "alarmAreaRanking",
    "alarmTrend"
  ],
  "timeRange": {
    "type": "today",
    "startTime": null,
    "endTime": null
  },
  "deviceType": "all",
  "areaIds": [],
  "alarmTypes": [],
  "handleMethods": [],
  "taskStatuses": [],
  "format": "PDF"
}
```

### 模块枚举

| 值 | 含义 |
|---|---|
| `equipmentRuntime` | 装备运行时长 |
| `aiAlarmAnalysis` | AI 告警分析 |
| `alarmAreaRanking` | 告警高发区域 |
| `alarmTrend` | 告警趋势图 |
| `taskCompletion` | 任务完成率 |

### 成功响应

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''statistics-report-20260615.pdf
```

响应体为 PDF 二进制文件流。

## 4. 后续真实数据来源建议

| 页面板块 | 建议权威来源 |
|---|---|
| KPI 总数、任务完成率 | 任务服务、告警服务聚合 |
| 装备运行时长 | 设备在线状态、故障状态、离线状态统计 |
| AI 告警分析 | 告警服务 |
| 告警高发区域 | 告警服务 + 区域/地图点位档案 |
| 告警趋势图 | 告警服务按日聚合 |
| 报告导出 | BFF 聚合统计数据后生成文件流 |
