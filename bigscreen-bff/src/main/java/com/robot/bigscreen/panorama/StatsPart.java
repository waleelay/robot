package com.robot.bigscreen.panorama;

/** 全景统计快照的可独立重算部分，用于按事件类型局部刷新。 */
public enum StatsPart {

    /** 设备统计：在线/离线/故障数量与类型分布。 */
    DEVICES,

    /** 任务与巡逻统计：今日任务、巡逻时长与里程。 */
    TASKS,

    /** 告警统计：告警分级数量与今日汇总。 */
    ALARMS;
}
