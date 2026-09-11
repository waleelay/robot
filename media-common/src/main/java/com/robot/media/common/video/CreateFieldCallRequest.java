package com.robot.media.common.video;

/**
 * 现场 App 视频呼叫会话创建请求（Control → Media 内网）。
 *
 * @param callId 呼叫 ID
 * @param orgId 组织 ID（用于隔离 Room 名）
 * @param appUserId 现场 App 用户 ID
 * @param centerUserId 接听操作员用户 ID
 * @param centerClientId 中心端客户端 ID
 */
public record CreateFieldCallRequest(
        String callId,
        String orgId,
        String appUserId,
        String centerUserId,
        String centerClientId) {
}
