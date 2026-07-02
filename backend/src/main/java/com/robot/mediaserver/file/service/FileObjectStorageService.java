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

@Service
public class FileObjectStorageService {

    private final MediaProperties properties;
    private MinioClient client;

    public FileObjectStorageService(MediaProperties properties) {
        this.properties = properties;
    }

    public String initiateMultipart() {
        requireEnabled();
        ensureBucket();
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String presignUploadPart(String objectKey, String storageUploadId, int partNumber) {
        requireEnabled();
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket())
                    .object(partKey(objectKey, storageUploadId, partNumber))
                    .method(Method.PUT)
                    .expiry(properties.getFile().getUploadUrlTtlSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("生成文件分片上传预签名地址失败", ex);
        }
    }

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
            throw new IllegalStateException("列出文件分片失败", ex);
        }
    }

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
            throw new IllegalStateException("合成文件对象失败", ex);
        }
    }

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
            throw new IllegalStateException("删除暂存文件分片失败", ex);
        }
    }

    public long statSize(String objectKey) {
        requireEnabled();
        try {
            StatObjectResponse response = client().statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
            return response.size();
        } catch (Exception ex) {
            throw new IllegalStateException("获取文件对象信息失败：" + objectKey, ex);
        }
    }

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
            throw new IllegalStateException("上传文件对象失败：" + objectKey, ex);
        }
    }

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
            throw new IllegalStateException("上传文件对象失败：" + objectKey, ex);
        }
    }

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
            throw new IllegalStateException("下载文件对象失败：" + objectKey, ex);
        }
    }

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
            throw new IllegalArgumentException("未找到文件对象：" + objectKey, ex);
        }
    }

    public String presignDownload(String objectKey, int ttlSeconds, String fileName, String contentType) {
        requireEnabled();
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .extraQueryParams(Map.of(
                            "response-content-disposition", contentDisposition(fileName),
                            "response-content-type", contentType == null || contentType.isBlank()
                                    ? "application/octet-stream"
                                    : contentType))
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("生成文件下载地址失败：" + objectKey, ex);
        }
    }

    private String contentDisposition(String fileName) {
        String fallback = safeFileName(fileName == null || fileName.isBlank() ? "download" : fileName)
                .replace("\"", "");
        String encoded = URLEncoder.encode(fileName == null || fileName.isBlank() ? fallback : fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

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
            throw new IllegalStateException("删除文件对象失败：" + prefix, ex);
        }
    }

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

    public String hlsPrefix(String objectKey) {
        int marker = objectKey.indexOf("/original/");
        if (marker < 0) {
            return objectKey + "/hls/";
        }
        return objectKey.substring(0, marker + 1) + "hls/";
    }

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
            throw new IllegalStateException("文件上传需要启用 MinIO");
        }
    }

    private void ensureBucket() {
        try {
            if (!client().bucketExists(BucketExistsArgs.builder().bucket(bucket()).build())) {
                client().makeBucket(MakeBucketArgs.builder().bucket(bucket()).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("准备文件存储桶失败", ex);
        }
    }

    private synchronized MinioClient client() {
        if (client == null) {
            client = MinioClient.builder()
                    .endpoint(properties.getMinio().getEndpoint())
                    .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                    .build();
        }
        return client;
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

    public record StoredPart(int partNumber, String etag, long size) {
    }
}
