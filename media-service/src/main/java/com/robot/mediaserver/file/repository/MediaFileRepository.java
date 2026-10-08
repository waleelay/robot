package com.robot.mediaserver.file.repository;

import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.model.MediaFile;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 查询文件主记录，支持租户过滤、来源复用和保留期清理。 */
public interface MediaFileRepository extends JpaRepository<MediaFile, String>, JpaSpecificationExecutor<MediaFile> {

    /**
     * 按机器人和来源文件标识查找同源文件。
     *
     * @param robotId 机器人 ID
     * @param sourceFileId 源文件 ID
     * @return 匹配文件；不存在时为空
     */
    Optional<MediaFile> findByRobotIdAndSourceFileId(String robotId, String sourceFileId);

    /**
     * 按类型与状态取最早更新的十条记录，供有界后台处理。
     *
     * @param fileType 文件类型
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @return 按更新时间升序排列的候选文件
     */
    List<MediaFile> findTop10ByFileTypeAndStatusOrderByUpdatedAtAsc(FileType fileType, FileStatus status);

    /**
     * 按类型、状态和来源前缀选择最早更新的候选文件。
     *
     * @param fileType 文件类型
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param sourceFileIdPrefix 来源文件标识的匹配前缀
     * @return 首个候选文件；不存在时为空
     */
    Optional<MediaFile> findFirstByFileTypeAndStatusAndSourceFileIdStartingWithOrderByUpdatedAtAsc(
            FileType fileType,
            FileStatus status,
            String sourceFileIdPrefix);

    /**
     * 按创建时间上界和来源前缀选择最多一百条待处理文件。
     *
     * @param fileType 文件类型
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param sourceFileIdPrefix 来源文件标识的匹配前缀
     * @param createdAt 创建时间
     * @return 按创建时间升序排列的候选文件
     */
    List<MediaFile> findTop100ByFileTypeAndStatusAndSourceFileIdStartingWithAndCreatedAtBeforeOrderByCreatedAtAsc(
            FileType fileType,
            FileStatus status,
            String sourceFileIdPrefix,
            OffsetDateTime createdAt);

    /**
     * 按状态和更新时间上界选择最多十条记录。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param updatedAt 更新时间
     * @return 按更新时间升序排列的候选文件
     */
    List<MediaFile> findTop10ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(FileStatus status, OffsetDateTime updatedAt);

    /**
     * 按状态选择最早更新的十条记录。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @return 本轮有界处理的文件列表
     */
    List<MediaFile> findTop10ByStatusOrderByUpdatedAtAsc(FileStatus status);
}
