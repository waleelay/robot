package com.robot.mediaserver.video.messaging;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import java.time.OffsetDateTime;

/**
 * 启动实时视频发布指令。
 *
 * <p>该指令通过 MQTT 下发到机器人端云接入客户端，客户端根据本地 RTSP 配置
 * 采集媒体源并发布到 LiveKit。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
public record VideoStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        String livekitUrl,
        String roomName,
        String publisherToken,
        String publishIdentity,
        String rtspUrl,
        OffsetDateTime expiresAt) {
}
