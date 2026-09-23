package com.robot.mediaserver.file.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.model.FileUploadMode;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.service.FileObjectStorageService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("unchecked")
class FileUploadProgressServiceTest {

    private final MediaProperties properties = new MediaProperties();
    private final MediaFileRepository fileRepository = mock(MediaFileRepository.class);
    private final MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
    private final FileObjectStorageService storage = mock(FileObjectStorageService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private FileUploadProgressService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(redis.opsForValue()).thenReturn(valueOperations);
        service = new FileUploadProgressService(
                properties,
                fileRepository,
                uploadRepository,
                storage,
                redis,
                Runnable::run);
    }

    @Test
    void returnsTerminalProgressAndMissingIdsWithoutAccessingStorage() {
        MediaFile file = file("file-1", FileStatus.READY, 100L);
        when(fileRepository.findAllById(any())).thenReturn(List.of(file));
        when(uploadRepository.findByFileIdInOrderByCreatedAtDesc(any())).thenReturn(List.of());

        FileUploadProgressQueryResponse response = service.query(List.of("file-1", "missing", "file-1"));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).phase()).isEqualTo(FileProgressPhase.READY);
        assertThat(response.items().get(0).uploadedBytes()).isEqualTo(100L);
        assertThat(response.items().get(0).uploadPercent()).isEqualTo(100.0d);
        assertThat(response.missingFileIds()).containsExactly("missing");
    }

    @Test
    void rebuildsActiveProgressFromCompletePartObjectsWhenRedisIsEmpty() {
        MediaFile file = file("file-1", FileStatus.UPLOADING, 20L);
        MediaFileUpload upload = upload(file.getFileId());
        when(fileRepository.findAllById(any())).thenReturn(List.of(file));
        when(uploadRepository.findByFileIdInOrderByCreatedAtDesc(any())).thenReturn(List.of(upload));
        when(hashOperations.entries(any())).thenReturn(Map.of());
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(storage.listParts(file.getObjectKey(), upload.getStorageUploadId())).thenReturn(List.of(
                new FileObjectStorageService.StoredPart(1, "etag-1", 5L),
                new FileObjectStorageService.StoredPart(2, "etag-2", 5L)));

        FileUploadProgressItem item = service.query(List.of(file.getFileId())).items().get(0);

        assertThat(item.phase()).isEqualTo(FileProgressPhase.UPLOADING);
        assertThat(item.uploadedBytes()).isEqualTo(10L);
        assertThat(item.uploadedPartCount()).isEqualTo(2);
        assertThat(item.uploadPercent()).isEqualTo(50.0d);
    }

    @Test
    void doesNotReportExpiredIncompleteUploadAsFullyUploaded() {
        MediaFile file = file("file-1", FileStatus.FAILED, 100L);
        MediaFileUpload upload = upload(file.getFileId());
        upload.setStatus(FileUploadStatus.EXPIRED);
        when(fileRepository.findAllById(any())).thenReturn(List.of(file));
        when(uploadRepository.findByFileIdInOrderByCreatedAtDesc(any())).thenReturn(List.of(upload));

        FileUploadProgressItem item = service.query(List.of(file.getFileId())).items().get(0);

        assertThat(item.phase()).isEqualTo(FileProgressPhase.EXPIRED);
        assertThat(item.uploadedBytes()).isZero();
        assertThat(item.uploadPercent()).isZero();
    }

    @Test
    void rejectsMalformedMinioPayload() {
        assertThatThrownBy(() -> service.acceptMinioEvent(new ObjectMapper().createObjectNode()))
                .isInstanceOf(FileApiException.class)
                .satisfies(exception -> assertThat(((FileApiException) exception).getCode())
                        .isEqualTo("INVALID_PART_EVENT"));
    }

    private MediaFile file(String fileId, FileStatus status, long size) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MediaFile file = new MediaFile();
        file.setFileId(fileId);
        file.setFileName("video.mp4");
        file.setFileType(FileType.VIDEO);
        file.setFileSize(size);
        file.setObjectKey("files/file-1/source.mp4");
        file.setUploadMode(FileUploadMode.MULTIPART);
        file.setStatus(status);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        return file;
    }

    private MediaFileUpload upload(String fileId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MediaFileUpload upload = new MediaFileUpload();
        upload.setUploadId("upl-1");
        upload.setFileId(fileId);
        upload.setStorageUploadId("storage-1");
        upload.setUploadMode(FileUploadMode.MULTIPART);
        upload.setPartSize(5L);
        upload.setPartCount(4);
        upload.setStatus(FileUploadStatus.ACTIVE);
        upload.setCreatedAt(now);
        upload.setLastActiveAt(now);
        upload.setExpiresAt(now.plusHours(1));
        return upload;
    }
}
