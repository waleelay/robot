package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.dto.FileStatusResponse;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.model.FileUploadMode;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.scheduling.TaskScheduler;

class FileServiceMultipartCompletionTest {

    @Test
    void completesDatabaseStateWhenComposedObjectAlreadyExists() {
        MediaFileRepository fileRepository = mock(MediaFileRepository.class);
        MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
        FileObjectStorageService storage = mock(FileObjectStorageService.class);
        FileService fileService = mock(FileService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new SimpleTransactionStatus());
        FileMultipartCompletionService service = new FileMultipartCompletionService(
                fileRepository,
                uploadRepository,
                storage,
                fileService,
                transactionManager,
                mock(TaskScheduler.class),
                new MediaProperties());
        MediaFile file = file();
        MediaFileUpload upload = upload(file.getFileId());
        when(uploadRepository.findByIdForUpdate(upload.getUploadId())).thenReturn(Optional.of(upload));
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(storage.statSizeIfExists(file.getObjectKey())).thenReturn(file.getFileSize());
        when(fileService.fileStatus(file.getRobotId(), file.getFileId())).thenReturn(new FileStatusResponse(
                file.getFileId(), "READY", file.getFileSize(), true, null, null, OffsetDateTime.now(ZoneOffset.UTC)));

        var response = service.complete(file.getRobotId(), upload.getUploadId());

        assertThat(response.status()).isEqualTo("READY");
        assertThat(upload.getStatus()).isEqualTo(FileUploadStatus.COMPLETED);
        verify(fileService).markMultipartUploaded(file.getFileId());
        verify(storage, never()).listParts(file.getObjectKey(), upload.getStorageUploadId());
        verify(storage, never()).completeMultipart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releasesCompletingStateWhenPartsAreIncomplete() {
        MediaFileRepository fileRepository = mock(MediaFileRepository.class);
        MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
        FileObjectStorageService storage = mock(FileObjectStorageService.class);
        FileService fileService = mock(FileService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new SimpleTransactionStatus());
        FileMultipartCompletionService service = new FileMultipartCompletionService(
                fileRepository,
                uploadRepository,
                storage,
                fileService,
                transactionManager,
                mock(TaskScheduler.class),
                new MediaProperties());
        MediaFile file = file();
        MediaFileUpload upload = upload(file.getFileId());
        upload.setStatus(FileUploadStatus.ACTIVE);
        when(uploadRepository.findByIdForUpdate(upload.getUploadId())).thenReturn(Optional.of(upload));
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(storage.statSizeIfExists(file.getObjectKey())).thenReturn(null);
        when(storage.listParts(file.getObjectKey(), upload.getStorageUploadId())).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.complete(file.getRobotId(), upload.getUploadId()))
                .isInstanceOf(FileApiException.class)
                .satisfies(exception -> assertThat(((FileApiException) exception).getCode())
                        .isEqualTo("UPLOAD_INCOMPLETE"));

        assertThat(upload.getStatus()).isEqualTo(FileUploadStatus.ACTIVE);
        verify(storage, never()).completeMultipart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsRetryableConflictWhileAnotherOwnerHoldsTheLease() {
        MediaFileRepository fileRepository = mock(MediaFileRepository.class);
        MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
        FileObjectStorageService storage = mock(FileObjectStorageService.class);
        FileService fileService = mock(FileService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(new SimpleTransactionStatus());
        FileMultipartCompletionService service = new FileMultipartCompletionService(
                fileRepository,
                uploadRepository,
                storage,
                fileService,
                transactionManager,
                mock(TaskScheduler.class),
                new MediaProperties());
        MediaFile file = file();
        MediaFileUpload upload = upload(file.getFileId());
        upload.setCompletionOwner("other-owner");
        upload.setCompletionLeaseExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        when(uploadRepository.findByIdForUpdate(upload.getUploadId())).thenReturn(Optional.of(upload));
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        assertThatThrownBy(() -> service.complete(file.getRobotId(), upload.getUploadId()))
                .isInstanceOfSatisfying(FileApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("UPLOAD_COMPLETION_IN_PROGRESS");
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getDetails()).containsKey("retryAfterSeconds");
                });
        verify(storage, never()).statSizeIfExists(file.getObjectKey());
        verify(storage, never()).completeMultipart(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MediaFile file() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MediaFile file = new MediaFile();
        file.setFileId("file-1");
        file.setOrgId("org001");
        file.setRobotId("robot-1");
        file.setFileName("artifact.bin");
        file.setContentType("application/octet-stream");
        file.setFileType(FileType.OTHER);
        file.setFileSize(10L);
        file.setObjectKey("files/file-1/source.bin");
        file.setUploadMode(FileUploadMode.MULTIPART);
        file.setStatus(FileStatus.UPLOADING);
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
        upload.setPartCount(2);
        upload.setStatus(FileUploadStatus.COMPLETING);
        upload.setCreatedAt(now);
        upload.setLastActiveAt(now);
        upload.setExpiresAt(now.plusHours(1));
        return upload;
    }
}
