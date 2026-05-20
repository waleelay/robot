package com.robot.mediaserver.storage;

import com.robot.mediaserver.config.MediaProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MinIO 对象存储服务。
 *
 * <p>用于保存抓拍图片、录像文件等媒体对象。默认通过配置关闭，便于没有 MinIO
 * 环境时先完成实时视频主链路开发。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MediaProperties properties;
    private MinioClient client;

    public MinioStorageService(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 上传媒体对象。
     *
     * @param objectKey 对象 Key
     * @param inputStream 文件流
     * @param size 文件大小
     * @param contentType 内容类型
     * @return 对象 Key
     */
    public String upload(String objectKey, InputStream inputStream, long size, String contentType) {
        if (!properties.getMinio().isEnabled()) {
            log.info("MinIO disabled, skip upload objectKey={}", objectKey);
            return objectKey;
        }
        try {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload object to MinIO: " + objectKey, ex);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client().bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .build());
        if (!exists) {
            client().makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .build());
        }
    }

    private synchronized MinioClient client() {
        if (client != null) {
            return client;
        }
        client = MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                .build();
        return client;
    }
}
