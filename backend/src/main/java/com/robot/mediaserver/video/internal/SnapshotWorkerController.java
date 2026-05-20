package com.robot.mediaserver.video.internal;

import com.robot.mediaserver.video.dto.CompleteSnapshotRequest;
import com.robot.mediaserver.video.dto.FailSnapshotRequest;
import com.robot.mediaserver.video.dto.SnapshotResponse;
import com.robot.mediaserver.video.service.SnapshotService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Snapshot Worker 内部回写接口。
 *
 * <p>后续服务端截帧 Worker 订阅 LiveKit Track 并生成图片后，通过该接口把正式
 * 抓拍结果写回媒体服务。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@RestController
@RequestMapping("/api/internal/media/snapshots")
public class SnapshotWorkerController {

    private final SnapshotService snapshotService;

    public SnapshotWorkerController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /**
     * 以 JSON 方式完成抓拍任务。
     *
     * @param snapshotId 抓拍任务 ID
     * @param request 完成请求
     * @return 抓拍响应
     */
    @PostMapping("/{snapshotId}/complete")
    public SnapshotResponse complete(
            @PathVariable String snapshotId,
            @RequestBody CompleteSnapshotRequest request) {
        return snapshotService.complete(snapshotId, request, null);
    }

    /**
     * 以 multipart 方式完成抓拍任务并上传图片。
     *
     * @param snapshotId 抓拍任务 ID
     * @param request 完成请求
     * @param file 抓拍图片
     * @return 抓拍响应
     */
    @PostMapping(value = "/{snapshotId}/complete-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SnapshotResponse completeFile(
            @PathVariable String snapshotId,
            @ModelAttribute CompleteSnapshotRequest request,
            @RequestPart("file") MultipartFile file) {
        return snapshotService.complete(snapshotId, request, file);
    }

    /**
     * 标记抓拍任务失败。
     *
     * @param snapshotId 抓拍任务 ID
     * @param request 失败请求
     * @return 抓拍响应
     */
    @PostMapping("/{snapshotId}/fail")
    public SnapshotResponse fail(
            @PathVariable String snapshotId,
            @RequestBody FailSnapshotRequest request) {
        return snapshotService.fail(snapshotId, request);
    }
}
