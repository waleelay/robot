package com.robot.mediaserver.file.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.service.FileService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileUploadCleanupSchedulerTest {

    @Test
    void continuesCleaningWhenOneExpiredUploadFails() {
        MediaFileUploadRepository repository = mock(MediaFileUploadRepository.class);
        FileService service = mock(FileService.class);
        MediaFileUpload first = upload("upload-1", "file-1");
        MediaFileUpload second = upload("upload-2", "file-2");
        when(repository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        eq(FileUploadStatus.ACTIVE), any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("storage unavailable")).when(service).expireUpload(first);

        new FileUploadCleanupScheduler(repository, service).cleanup();

        verify(service).expireUpload(first);
        verify(service).expireUpload(second);
    }

    private MediaFileUpload upload(String uploadId, String fileId) {
        MediaFileUpload upload = new MediaFileUpload();
        upload.setUploadId(uploadId);
        upload.setFileId(fileId);
        upload.setStatus(FileUploadStatus.ACTIVE);
        return upload;
    }
}
