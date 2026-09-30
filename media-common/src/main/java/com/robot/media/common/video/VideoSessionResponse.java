package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 视频会话响应。
 * @author leelay
 * @date 2026-07-05
 * @param sessionId 会话 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param channel 视频通道
 * @param quality 视频清晰度
 * @param status 状态
 * @param roomName LiveKit 房间名
 * @param livekitUrl LiveKit 地址
 * @param viewerToken 观看 Token
 * @param trackSid 媒体轨道标识
 * @param trackName Track 名称
 * @param viewerCount 观看人数
 * @param intercomStatus 对讲状态
 * @param intercomAudioOnly 是否仅对讲音频
 * @param intercomOperatorId 对讲操作员 ID
 * @param intercomClientId 对讲客户端 ID
 * @param robotAudioTrackSid 机器人音频 媒体轨道标识
 * @param robotAudioTrackName 机器人音频 Track 名称
 * @param lastErrorCode 最后错误码
 * @param lastErrorMessage 最后错误消息
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param sourceType 视频来源类型
 * @param sourceId 归一化后的视频源标识；机器人来源对应机器人，固定摄像头来源对应摄像头
 * @param publisherMode 当前发布模式
 * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
 */
@Schema(name = "VideoSessionResponse", description = "视频会话响应。")
public record VideoSessionResponse(
        @Schema(
                description = "视频会话 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sessionId,
        @Schema(
                description = "机器人 ID；固定摄像头场景按现有来源映射填写",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String robotId,
        @Schema(
                description = "视频来源类型",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoSourceType sourceType,
        @Schema(
                description = "视频源 ID；创建请求缺省时使用 robotId",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sourceId,
        @Schema(
                description = "当前发布模式",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoPublisherMode publisherMode,
        @Schema(description = "发布模式版本，用于拒绝陈旧发布者操作", requiredMode = Schema.RequiredMode.REQUIRED) long publisherRevision,
        @Schema(
                description = "相机或设备组件 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String deviceId,
        @Schema(
                description = "视频通道",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoChannel channel,
        @Schema(
                description = "视频清晰度；缺省取该入口的 DTO 默认值",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoQuality quality,
        @Schema(
                description = "当前状态，具体状态转换见实时视频协议",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoSessionStatus status,
        @Schema(
                description = "LiveKit 房间名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String roomName,
        @Schema(
                description = "LiveKit 连接地址",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String livekitUrl,
        @Schema(
                description = "观看令牌，仅返回给当前观看者，不得写入日志",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String viewerToken,
        @Schema(
                description = "LiveKit 轨道标识",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String trackSid,
        @Schema(
                description = "媒体轨道名称",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String trackName,
        @Schema(description = "当前有效观看者数量", requiredMode = Schema.RequiredMode.REQUIRED) int viewerCount,
        @Schema(
                description = "对讲状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        IntercomStatus intercomStatus,
        @Schema(description = "是否仅对讲音频", requiredMode = Schema.RequiredMode.REQUIRED) boolean intercomAudioOnly,
        @Schema(
                description = "占用对讲的操作员 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String intercomOperatorId,
        @Schema(
                description = "占用对讲的终端标识",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String intercomClientId,
        @Schema(
                description = "机器人音频 Track SID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String robotAudioTrackSid,
        @Schema(
                description = "机器人音频轨道名称",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String robotAudioTrackName,
        @Schema(
                description = "最近业务错误码",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String lastErrorCode,
        @Schema(
                description = "最近错误说明",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String lastErrorMessage,
        @Schema(
                description = "创建时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime createdAt,
        @Schema(
                description = "更新时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime updatedAt) {
}
