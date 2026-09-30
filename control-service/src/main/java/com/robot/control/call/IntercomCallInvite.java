package com.robot.control.call;

import java.time.OffsetDateTime;

/**
 * 机器人向控制中心发起对讲呼叫时发送的 MQTT 邀请载荷。
 *
 * <p>由 MQTT 订阅器反序列化后交给 {@link IntercomCallService} 登记待接听呼叫；
 * 呼叫创建时间和振铃期限以控制服务接收并处理邀请的时间为准。</p>
 *
 * @param callId 呼叫标识，不能为空；重复邀请复用该标识对应的现有呼叫
 * @param robotId 发起呼叫的机器人标识，不能为空且必须与 MQTT 主题中的机器人标识一致
 * @param deviceId 对讲关联的摄像头设备标识，不能为空；接听时用于创建媒体会话
 * @param channel 视频通道，空值使用 visible；接听时无法识别的值也回退为 visible
 * @param quality 视频清晰度，空值使用 sub；接听时无法识别的值也回退为 sub
 * @param reason 发起呼叫的原因说明，空值使用“机器人请求人工对讲”
 * @param timeoutSeconds 振铃等待时长，单位秒；未提供时为 30，实际值限制在 5 至 120 之间
 * @param timestamp 机器人发送邀请的时间，可为空；当前服务不使用该字段计算振铃期限
 */
public record IntercomCallInvite(
        String callId,
        String robotId,
        String deviceId,
        String channel,
        String quality,
        String reason,
        Integer timeoutSeconds,
        OffsetDateTime timestamp) {
}
