package com.robot.control.call;

/**
 * 现场应用 视频呼叫状态。
 */
public enum FieldCallStatus {
    /**
     * 邀请已发出，等待接听或拒绝。
     */
    RINGING,
    /**
     * 邀请已接受，后续媒体或通话生命周期继续由会话管理。
     */
    ACCEPTED,
    /**
     * 被邀请方明确拒绝本次呼叫。
     */
    REJECTED,
    /**
     * 等待应答超过本次呼叫期限。
     */
    TIMEOUT,
    /**
     * 发起方撤销尚未完成的呼叫。
     */
    CANCELED,
    /**
     * 目标已被其他通话或对讲占用。
     */
    BUSY,
    /**
     * 呼叫处理失败，保留原因供调用方判断。
     */
    FAILED,
    /**
     * 通话已结束并释放对应占用。
     */
    ENDED
}
