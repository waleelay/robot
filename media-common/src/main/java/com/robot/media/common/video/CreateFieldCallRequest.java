package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 现场应用 视频呼叫会话创建请求（Control → Media 内网）。
 *
 * @param callId 呼叫 ID
 * @param orgId 组织 ID（用于隔离 Room 名）
 * @param appUserId 现场应用 用户 ID
 * @param centerUserId 接听操作员用户 ID
 * @param centerClientId 中心端客户端 ID
 */
@Schema(name = "CreateFieldCallRequest", description = "现场 App 视频呼叫会话创建请求（Control → Media 内网）。")
public record CreateFieldCallRequest(
        @Schema(description = "呼叫 ID", types = {"string", "null"}) String callId,
        @Schema(description = "组织 ID", types = {"string", "null"}) String orgId,
        @Schema(description = "现场 App 用户 ID", types = {"string", "null"}) String appUserId,
        @Schema(description = "指挥中心接听用户 ID", types = {"string", "null"}) String centerUserId,
        @Schema(description = "指挥中心终端标识", types = {"string", "null"}) String centerClientId) {
}
