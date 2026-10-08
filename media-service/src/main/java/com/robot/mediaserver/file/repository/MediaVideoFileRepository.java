package com.robot.mediaserver.file.repository;

import com.robot.mediaserver.file.model.MediaVideoFile;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按文件 ID 存取视频探测、HLS 处理状态及播放资源位置。 */
public interface MediaVideoFileRepository extends JpaRepository<MediaVideoFile, String> {
}
