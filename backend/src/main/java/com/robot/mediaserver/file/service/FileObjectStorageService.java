package com.robot.mediaserver.file.service;

import com.robot.mediaserver.config.MediaProperties;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.DownloadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** 封装 MinIO 对象和分片读写、签名地址及桶初始化；调用方负责传入流的生命周期。 */
@Service
public class FileObjectStorageService {

    private final MediaProperties properties;
    /**
     * 用于内部对象读写的延迟初始化客户端，由同步访问方法保证单实例初始化。
     */
    private MinioClient client;
    /**
     * 使用对外上传端点签名的客户端；不能与内部访问端点混用。
     */
    private MinioClient presignClient;
    /**
     * 使用对外下载端点签名的客户端，与上传端点分别配置。
     */
    private MinioClient downloadPresignClient;

    /**
     * 初始化 FileObjectStorageService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     */
    public FileObjectStorageService(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 检查存储可用性与桶后生成暂存分片命名空间 ID；当前实现使用普通对象暂存分片。
     *
     * @return 平台生成的分片会话标识，并非原生 S3 分片上传标识
     */
    public String initiateMultipart() {
        requireEnabled();
        ensureBucket();
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 为指定分片签发临时上传地址。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @param partNumber 从 1 开始的分片编号
     * @return 限定对象、上传会话和分片号的预签名 URL
     */
    public String presignUploadPart(String objectKey, String storageUploadId, int partNumber) {
        requireEnabled();
        try {
            return presignClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket())
                    .object(partKey(objectKey, storageUploadId, partNumber))
                    .method(Method.PUT)
                    .expiry(properties.getFile().getUploadUrlTtlSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("生成文件分片上传预签名地址失败", ex);
        }
    }

    /**
     * 读取对象存储已确认的分片列表，作为合并与进度校验依据。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @return 已上传分片的编号、ETag 和大小
     */
    public List<StoredPart> listParts(String objectKey, String storageUploadId) {
        requireEnabled();
        try {
            List<StoredPart> parts = new ArrayList<>();
            String prefix = partsPrefix(objectKey, storageUploadId);
            for (var result : client().listObjects(ListObjectsArgs.builder()
                    .bucket(bucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build())) {
                Item item = result.get();
                Integer partNumber = parsePartNumber(item.objectName());
                if (partNumber != null) {
                    parts.add(new StoredPart(partNumber, item.etag(), item.size()));
                }
            }
            parts.sort(Comparator.comparingInt(StoredPart::partNumber));
            return parts;
        } catch (Exception ex) {
            throw new FileStorageException("列出文件分片失败", ex);
        }
    }

    /**
     * 按已校验的分片顺序组合暂存对象为源文件，成功后删除暂存分片。
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     * @param parts 按合并顺序排列且已完成校验的存储分片
     */
    public void completeMultipart(String objectKey, String storageUploadId, List<StoredPart> parts) {
        requireEnabled();
        try {
            List<ComposeSource> sources = parts.stream()
                    .map(part -> ComposeSource.builder()
                            .bucket(bucket())
                            .object(partKey(objectKey, storageUploadId, part.partNumber()))
                            .build())
                    .toList();
            client().composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .sources(sources)
                    .build());
            abortMultipart(objectKey, storageUploadId);
        } catch (Exception ex) {
            throw new FileStorageException("合成文件对象失败", ex);
        }
    }

    /**
     * 删除该平台分片会话下的暂存分片对象。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param storageUploadId 平台生成的暂存分片会话标识，用于组织分片对象键
     */
    public void abortMultipart(String objectKey, String storageUploadId) {
        requireEnabled();
        try {
            for (StoredPart part : listParts(objectKey, storageUploadId)) {
                client().removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket())
                        .object(partKey(objectKey, storageUploadId, part.partNumber()))
                        .build());
            }
        } catch (Exception ex) {
            throw new FileStorageException("删除暂存文件分片失败", ex);
        }
    }

    /**
     * 查询对象的实际存储大小。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return 对象大小，单位字节
     */
    public long statSize(String objectKey) {
        requireEnabled();
        try {
            StatObjectResponse response = client().statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
            return response.size();
        } catch (Exception ex) {
            throw new FileStorageException("获取文件对象信息失败：" + objectKey, ex);
        }
    }

    /**
     * 返回对象大小；对象不存在时返回 null，其他存储异常仍向上抛出。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return 实际对象字节数；对象不存在时为 null
     */
    public Long statSizeIfExists(String objectKey) {
        requireEnabled();
        try {
            return client().statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build()).size();
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() == null ? null : ex.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NotFound".equals(code)) {
                return null;
            }
            throw new FileStorageException("获取文件对象信息失败：" + objectKey, ex);
        } catch (Exception ex) {
            throw new FileStorageException("获取文件对象信息失败：" + objectKey, ex);
        }
    }

    /**
     * 将输入流上传为指定对象；输入流的生命周期由调用方管理。
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param inputStream 待写入的内容流；调用方负责管理流生命周期
     * @param size 待上传内容的字节数
     * @param contentType 文件媒体类型
     */
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        requireEnabled();
        ensureBucket();
        try {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("上传文件对象失败：" + objectKey, ex);
        }
    }

    /**
     * 将本地文件写入对象存储，供后处理产物发布使用。
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param source 待上传的本地文件路径
     * @param contentType 文件媒体类型
     */
    public void uploadFile(String objectKey, Path source, String contentType) {
        requireEnabled();
        ensureBucket();
        try {
            client().uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .filename(source.toString())
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("上传文件对象失败：" + objectKey, ex);
        }
    }

    /**
     * 将存储对象下载到指定本地路径。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param destination 输出内容的本地目标路径
     */
    public void download(String objectKey, Path destination) {
        requireEnabled();
        try {
            client().downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .filename(destination.toString())
                    .overwrite(true)
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("下载文件对象失败：" + objectKey, ex);
        }
    }

    /**
     * 将对象正文完整读入内存；本方法不限制大小，调用方须先校验代理读取上限。
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return 对象二进制正文
     */
    public byte[] readObject(String objectKey) {
        requireEnabled();
        try (InputStream input = client().getObject(GetObjectArgs.builder()
                .bucket(bucket())
                .object(objectKey)
                .build());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new FileStorageException("读取文件对象失败：" + objectKey, ex);
        }
    }

    /**
     * 签发临时下载地址，并按当前配置使用对外端点。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param ttlSeconds 有效期限，单位秒
     * @param fileName 原始文件名
     * @param contentType 文件媒体类型
     * @return 带有效期限的下载 URL，不应写入日志
     */
    public String presignDownload(String objectKey, int ttlSeconds, String fileName, String contentType) {
        return presignDownload(objectKey, ttlSeconds, fileName, contentType, false);
    }

    /**
     * 签发临时下载地址，并按当前配置使用对外端点。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @param ttlSeconds 有效期限，单位秒
     * @param fileName 原始文件名
     * @param contentType 文件媒体类型
     * @param inline 是否使用内联展示的响应方式
     * @return 带有效期限的下载 URL，不应写入日志
     */
    public String presignDownload(String objectKey, int ttlSeconds, String fileName, String contentType, boolean inline) {
        requireEnabled();
        try {
            return downloadPresignClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .extraQueryParams(Map.of(
                            "response-content-disposition", contentDisposition(fileName, inline),
                            "response-content-type", contentType == null || contentType.isBlank()
                                    ? "application/octet-stream"
                                    : contentType))
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("生成文件下载地址失败：" + objectKey, ex);
        }
    }

    private String contentDisposition(String fileName, boolean inline) {
        if (inline) {
            return "inline";
        }
        String fallback = safeFileName(fileName == null || fileName.isBlank() ? "download" : fileName)
                .replace("\"", "");
        String encoded = URLEncoder.encode(fileName == null || fileName.isBlank() ? fallback : fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 清理指定目录前缀下的存储对象，供文件及 HLS 资产删除使用。
     *
     * @param prefix 需要匹配或清理的对象键前缀
     */
    public void deletePrefix(String prefix) {
        requireEnabled();
        try {
            for (var result : client().listObjects(ListObjectsArgs.builder()
                    .bucket(bucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build())) {
                client().removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket())
                        .object(result.get().objectName())
                        .build());
            }
        } catch (Exception ex) {
            throw new FileStorageException("删除文件对象失败：" + prefix, ex);
        }
    }

    /**
     * 构造文件源对象的存储键，将同一文件资产归入统一目录。
     *
     * @param orgId 组织 ID
     * @param robotId 机器人 ID
     * @param fileId 文件 ID
     * @param fileName 原始文件名
     * @param timestamp 状态时间
     * @return 源文件对象键
     */
    public String buildObjectKey(String orgId, String robotId, String fileId, String fileName, OffsetDateTime timestamp) {
        String owner = robotId == null || robotId.isBlank() ? "system" : safePath(robotId);
        return "files/%s/%s/%04d/%02d/%02d/%s/original/%s".formatted(
                safePath(orgId),
                owner,
                timestamp.getYear(),
                timestamp.getMonthValue(),
                timestamp.getDayOfMonth(),
                safePath(fileId),
                safeFileName(fileName));
    }

    /**
     * 取得指定文件 HLS 播放列表与分片的目录前缀。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return HLS 对象键前缀
     */
    public String hlsPrefix(String objectKey) {
        int marker = objectKey.indexOf("/original/");
        if (marker < 0) {
            return objectKey + "/hls/";
        }
        return objectKey.substring(0, marker + 1) + "hls/";
    }

    /**
     * 取得指定文件全部存储资产的根目录前缀。
     *
     * @param objectKey 对象在存储桶内的键，不包含访问凭据
     * @return 文件资产根前缀
     */
    public String fileRootPrefix(String objectKey) {
        int marker = objectKey.indexOf("/original/");
        if (marker < 0) {
            return objectKey;
        }
        return objectKey.substring(0, marker + 1);
    }

    private String partKey(String objectKey, String storageUploadId, int partNumber) {
        return partsPrefix(objectKey, storageUploadId) + "%06d".formatted(partNumber);
    }

    private String partsPrefix(String objectKey, String storageUploadId) {
        return objectKey + ".upload-parts/" + storageUploadId + "/part-";
    }

    private Integer parsePartNumber(String objectName) {
        int marker = objectName.lastIndexOf("/part-");
        if (marker < 0) {
            return null;
        }
        try {
            return Integer.valueOf(objectName.substring(marker + 6));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void requireEnabled() {
        if (!properties.getMinio().isEnabled()) {
            throw new FileStorageException("文件服务未启用对象存储");
        }
    }

    private void ensureBucket() {
        try {
            if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket()).build())) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
            }
        } catch (Exception ex) {
            throw new FileStorageException("准备文件存储桶失败", ex);
        }
    }

    private synchronized MinioClient client() {
        if (client == null) {
            client = MinioClient.builder()
                    .endpoint(properties.getMinio().getEndpoint())
                    .region(properties.getMinio().getRegion())
                    .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                    .build();
        }
        return client;
    }

    private synchronized MinioClient presignClient() {
        if (presignClient == null) {
            presignClient = MinioClient.builder()
                    .endpoint(properties.getMinio().getPublicEndpoint())
                    .region(properties.getMinio().getRegion())
                    .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                    .build();
        }
        return presignClient;
    }

    private synchronized MinioClient downloadPresignClient() {
        if (downloadPresignClient == null) {
            downloadPresignClient = MinioClient.builder()
                    .endpoint(properties.getMinio().getDownloadPublicEndpoint())
                    .region(properties.getMinio().getRegion())
                    .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                    .build();
        }
        return downloadPresignClient;
    }

    private String bucket() {
        return properties.getMinio().getBucket();
    }

    private String safePath(String value) {
        return (value == null || value.isBlank() ? "unknown" : value).replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String safeFileName(String value) {
        return (value == null || value.isBlank() ? "file" : value).replace("/", "_").replace("\\", "_");
    }

    /**
     * 对象存储中已完成分片的编号、ETag 与实际字节数。
     * @param partNumber 从 1 开始的分片编号
     * @param etag 对象存储分片 ETag
     * @param size 该分片的实际字节数
     */
    public record StoredPart(int partNumber, String etag, long size) {
    }
}
