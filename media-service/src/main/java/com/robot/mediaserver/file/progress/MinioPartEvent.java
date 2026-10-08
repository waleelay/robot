package com.robot.mediaserver.file.progress;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 仅描述进度服务实际读取的 MinIO 通知字段；运行时继续用 JsonNode 接收扩展字段。
 *
 * @param records 对象事件数组；启用进度处理时必须提供
 */
public record MinioPartEvent(
        @Schema(description = "对象事件数组；启用进度处理时必须提供", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("Records") List<PartRecord> records) {

    /**
     * 无关事件名和对象键会被忽略。
     *
     * @param eventName 仅处理 s3:ObjectCreated: 前缀的事件
     * @param s3 对象信息
     */
    public record PartRecord(
            @Schema(description = "仅处理 s3:ObjectCreated: 前缀的事件") String eventName,
            @Schema(description = "对象信息") StorageEvent s3) {
    }

    /**
     * 保留对象存储通知中 s3.object 的结构。
     *
     * @param object 本次事件涉及的对象
     */
    @Schema(description = "对象存储通知中的对象信息")
    public record StorageEvent(
            @Schema(description = "本次事件涉及的对象") PartObject object) {
    }

    /**
     * 服务用对象键提取存储上传标识和分片号，再校验字节数及 ETag。
     *
     * @param key URL 编码对象键；分片键包含 .upload-parts/{storageUploadId}/part-{number}
     * @param size 分片字节数；匹配的分片事件必须为非负值
     * @param eTag 对象 ETag；缺失时按空字符串处理
     */
    @Schema(description = "本次事件涉及的对象键、字节数与 ETag")
    public record PartObject(
            @Schema(description = "URL 编码对象键；分片键包含 .upload-parts/{storageUploadId}/part-{number}") String key,
            @Schema(description = "分片字节数；匹配的分片事件必须为非负值") Long size,
            @Schema(description = "对象 ETag；缺失时按空字符串处理") String eTag) {
    }
}
