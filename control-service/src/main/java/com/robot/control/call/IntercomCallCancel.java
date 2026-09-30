package com.robot.control.call;

import java.time.OffsetDateTime;

/**
 * 机器人撤销尚在振铃中的呼叫时发送的 MQTT 取消载荷。
 *
 * <p>{@link IntercomCallService} 仅撤销指定机器人仍处于 RINGING 状态的呼叫；
 * 呼叫不存在、已离开振铃状态或 MQTT 主题中的机器人与呼叫归属不一致时忽略消息。</p>
 *
 * @param callId 待撤销的呼叫标识，为空时忽略消息
 * @param robotId 载荷携带的机器人标识；当前服务以 MQTT 主题中的机器人标识核对呼叫归属
 * @param reason 撤销呼叫的原因说明，空值使用既有消息值 robot canceled
 * @param timestamp 机器人发送撤销消息的时间，可为空；呼叫更新时间以控制服务处理时间为准
 */
public record IntercomCallCancel(
        String callId,
        String robotId,
        String reason,
        OffsetDateTime timestamp) {
}
