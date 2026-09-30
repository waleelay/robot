package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 固定摄像头 LiveKit Ingress 配置与状态。
 *
 * @param cameraId 固定摄像头 ID
 * @param ingressId LiveKit 接入资源标识
 * @param publisherMode 当前发布模式
 * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
 * @param ingressOperationRevision Ingress 管理操作版本
 * @param configured 是否已经配置 Ingress
 * @param roomName LiveKit 房间名
 * @param participantIdentity LiveKit 参与者身份
 * @param streamStatus Ingress 推流状态
 * @param reasonCode 当前状态或失败原因码
 * @param observedAt 服务端观察时间
 * @param credentialIssued 本次响应是否包含新签发凭据
 * @param url Ingress 推流地址，仅可信管理调用可见
 * @param streamKey Ingress 推流密钥，仅签发时返回，不得写入日志
 */
@Schema(name = "FixedCameraIngressResponse", description = "固定摄像头 LiveKit Ingress 配置与状态。")
public record FixedCameraIngressResponse(
        @Schema(
                description = "固定摄像头 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String cameraId,
        @Schema(
                description = "LiveKit 接入资源标识",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String ingressId,
        @Schema(
                description = "当前发布模式",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoPublisherMode publisherMode,
        @Schema(description = "发布模式版本，用于拒绝陈旧发布者操作", requiredMode = Schema.RequiredMode.REQUIRED) long publisherRevision,
        @Schema(
                description = "Ingress 管理操作版本",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long ingressOperationRevision,
        @Schema(description = "是否已经配置 Ingress", requiredMode = Schema.RequiredMode.REQUIRED) boolean configured,
        @Schema(
                description = "LiveKit 房间名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String roomName,
        @Schema(
                description = "LiveKit 参与者身份",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String participantIdentity,
        @Schema(
                description = "Ingress 推流状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String streamStatus,
        @Schema(
                description = "当前状态或失败原因码",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String reasonCode,
        @Schema(
                description = "服务端观察时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime observedAt,
        @Schema(description = "本次响应是否包含新签发凭据", requiredMode = Schema.RequiredMode.REQUIRED) boolean credentialIssued,
        @Schema(
                description = "Ingress 推流地址，仅可信管理调用可见",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String url,
        @Schema(
                description = "Ingress 推流密钥，仅签发时返回，不得写入日志",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String streamKey) {
}
