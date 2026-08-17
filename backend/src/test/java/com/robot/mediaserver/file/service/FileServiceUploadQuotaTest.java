package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.dto.CreateMultipartFileUploadRequest;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FileServiceUploadQuotaTest {

    private final MediaFileRepository fileRepository = mock(MediaFileRepository.class);
    private final MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
    private final MediaVideoFileRepository videoRepository = mock(MediaVideoFileRepository.class);
    private final FileObjectStorageService storage = mock(FileObjectStorageService.class);
    private final LiveKitEgressService egressService = mock(LiveKitEgressService.class);
    private final MediaProperties properties = new MediaProperties();
    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(
                properties,
                fileRepository,
                uploadRepository,
                videoRepository,
                storage,
                egressService,
                new ObjectMapper());
        when(fileRepository.findByRobotIdAndSourceFileId("robot-1", "source-1"))
                .thenReturn(Optional.empty());
        when(storage.buildObjectKey(any(), any(), any(), any(), any())).thenReturn("files/test.bin");
        when(uploadRepository.countByStatusAndExpiresAtAfter(
                        any(FileUploadStatus.class), any(OffsetDateTime.class)))
                .thenReturn(0L);
    }

    @Test
    void returnsRobotSessionCapacityDetailsWhenLimitIsReached() {
        when(uploadRepository.countActiveByRobotId(
                        any(), any(FileUploadStatus.class), any(OffsetDateTime.class)))
                .thenReturn(20L);

        assertThatThrownBy(() -> service.createOrResumeMultipart("robot-1", request()))
                .isInstanceOf(FileApiException.class)
                .satisfies(ex -> {
                    FileApiException apiException = (FileApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(apiException.getCode()).isEqualTo("UPLOAD_SESSION_LIMIT");
                    assertThat(apiException.isRetryable()).isTrue();
                    assertThat(apiException.getDetails()).containsEntry("scope", "ROBOT");
                    assertThat(apiException.getDetails()).containsEntry("robotId", "robot-1");
                    assertThat(apiException.getDetails()).containsEntry("activeCount", 20L);
                    assertThat(apiException.getDetails()).containsEntry("limit", 20);
                });
    }

    private CreateMultipartFileUploadRequest request() {
        CreateMultipartFileUploadRequest request = new CreateMultipartFileUploadRequest();
        request.setRobotId("robot-1");
        request.setSourceFileId("source-1");
        request.setFileType(FileType.OTHER);
        request.setFileName("test.bin");
        request.setContentType("application/octet-stream");
        request.setFileSize(1024);
        return request;
    }
}
