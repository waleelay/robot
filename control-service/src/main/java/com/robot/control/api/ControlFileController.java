package com.robot.control.api;

import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.dto.FileDownloadUrlResponse;
import com.robot.control.dto.FileListItemResponse;
import com.robot.control.dto.FileListResponse;
import com.robot.control.dto.FilePlayUrlResponse;
import com.robot.control.dto.FileStatus;
import com.robot.control.dto.FileType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 面向前端的控制侧文件代理接口。
 *
 * @author leelay
 * @date 2026-07-05
 */
@RestController
@RequestMapping("/api/control/files")
public class ControlFileController {

    private final ControlMediaServiceClient mediaServiceClient;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 创建 ControlFileController 实例。
     *
     * @param mediaServiceClient Media Service 客户端
     * @param currentUserResolver 当前用户解析器
     */
    public ControlFileController(ControlMediaServiceClient mediaServiceClient, CurrentUserResolver currentUserResolver) {
        this.mediaServiceClient = mediaServiceClient;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 代理上传前端提交的简单文件。
     *
     * @param file 上传文件
     * @param fileType 文件类型
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param sourceFileId 源文件 ID
     * @param metadata 扩展元数据
     * @param request 请求参数
     * @return 文件信息
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileListItemResponse uploadSimple(
            @RequestPart("file") MultipartFile file,
            @RequestParam FileType fileType,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String extensionId,
            @RequestParam(required = false) String sourceFileId,
            @RequestParam(required = false) String metadata,
            HttpServletRequest request) {
        return mediaServiceClient.uploadSimpleFile(
                file,
                fileType,
                robotId,
                deviceId,
                extensionId,
                sourceFileId,
                metadata,
                currentUserResolver.resolve(request));
    }

    /**
     * 列出当前注册的机器人状态。
     *
     * @param robotId 机器人 ID
     * @param deviceId 设备 ID
     * @param extensionId 通用扩展 ID
     * @param fileType 文件类型
     * @param status 状态消息
     * @param page page
     * @param size size
     * @param request 请求参数
     * @return 列表结果
     */
    @GetMapping
    public FileListResponse list(
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String extensionId,
            @RequestParam(required = false) FileType fileType,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return mediaServiceClient.files(
                robotId,
                deviceId,
                extensionId,
                fileType,
                status,
                page,
                size,
                currentUserResolver.resolve(request));
    }

    /**
     * 查询文件详情。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 文件详情
     */
    @GetMapping("/{fileId}")
    public FileListItemResponse detail(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.file(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 生成文件下载地址。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 下载地址响应
     */
    @PostMapping("/{fileId}/download-url")
    public FileDownloadUrlResponse downloadUrl(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.fileDownloadUrl(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 读取文件原始内容。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 文件内容响应
     */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> content(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.fileContent(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 生成文件播放地址。
     *
     * @param fileId 文件 ID
     * @param request 请求参数
     * @return 播放地址响应
     */
    @PostMapping("/{fileId}/play-url")
    public FilePlayUrlResponse playUrl(@PathVariable String fileId, HttpServletRequest request) {
        return mediaServiceClient.filePlayUrl(fileId, currentUserResolver.resolve(request));
    }

    /**
     * 代理读取 HLS 播放资源。
     *
     * @param fileId 文件 ID
     * @param objectName 对象名称
     * @param token 访问令牌
     * @return HLS 资源响应
     */
    @GetMapping("/{fileId}/hls/{objectName}")
    public ResponseEntity<byte[]> hls(
            @PathVariable String fileId,
            @PathVariable String objectName,
            @RequestParam String token) {
        byte[] body = mediaServiceClient.fileHlsAsset(fileId, objectName, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(hlsContentType(objectName)))
                .body(body);
    }

    /**
     * 根据 HLS 对象名推导响应 Content-Type。
     *
     * @param objectName 对象名称
     * @return Content-Type 字符串
     */
    private String hlsContentType(String objectName) {
        if (objectName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (objectName.endsWith(".m4s") || objectName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (objectName.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
