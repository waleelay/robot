package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.model.FileUploadMode;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaVideoFile;
import com.robot.mediaserver.file.model.VideoFileStatus;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileServiceLiveRecordingTest {

    private final MediaFileRepository fileRepository = mock(MediaFileRepository.class);
    private final MediaFileUploadRepository uploadRepository = mock(MediaFileUploadRepository.class);
    private final MediaVideoFileRepository videoRepository = mock(MediaVideoFileRepository.class);
    private final FileObjectStorageService storage = mock(FileObjectStorageService.class);
    private final LiveKitEgressService egressService = mock(LiveKitEgressService.class);
    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(
                new MediaProperties(),
                fileRepository,
                uploadRepository,
                videoRepository,
                storage,
                egressService,
                new ObjectMapper());
        when(videoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void treatsMissingEgressWithoutStoredFileAsStoppedFailure() {
        MediaFile file = recording();
        MediaVideoFile video = video(file);
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(videoRepository.findById(file.getFileId())).thenReturn(Optional.of(video));
        doThrow(new IllegalStateException("404 Not Found: egress not found"))
                .when(egressService)
                .stop("EG_missing");
        when(storage.statSize(file.getObjectKey())).thenThrow(new FileStorageException("文件不存在"));

        var response = service.stopLiveRecording("vs-1", file.getFileId(), user());

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(file.getErrorCode()).isEqualTo("EGRESS_ABORTED");
        assertThat(response.videoStatus()).isEqualTo("FAILED");
        assertThat(video.getErrorCode()).isEqualTo("EGRESS_ABORTED");
    }

    @Test
    void processesStoredFileWhenEgressAlreadyDisappeared() {
        MediaFile file = recording();
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(videoRepository.findById(file.getFileId())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("404 Not Found: egress not found"))
                .when(egressService)
                .stop("EG_missing");
        when(storage.statSize(file.getObjectKey())).thenReturn(1024L);

        var response = service.stopLiveRecording("vs-1", file.getFileId(), user());

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(file.getFileSize()).isEqualTo(1024L);
        assertThat(file.getErrorCode()).isNull();
    }

    @Test
    void repeatedStopReturnsCurrentStateWithoutStoppingEgressAgain() {
        MediaFile file = recording();
        file.setStatus(FileStatus.PROCESSING);
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(videoRepository.findById(file.getFileId())).thenReturn(Optional.of(video(file)));

        var response = service.stopLiveRecording("vs-1", file.getFileId(), user());

        assertThat(response.status()).isEqualTo("PROCESSING");
        verifyNoInteractions(egressService);
    }

    @Test
    void findsExpiredLiveRecording() {
        MediaFile file = recording();
        file.setSourceFileId("livekit-egress:vs-1:1");
        file.setCreatedAt(OffsetDateTime.now().minusHours(5));
        when(fileRepository
                .findTop100ByFileTypeAndStatusAndSourceFileIdStartingWithAndCreatedAtBeforeOrderByCreatedAtAsc(
                        org.mockito.ArgumentMatchers.eq(FileType.VIDEO),
                        org.mockito.ArgumentMatchers.eq(FileStatus.UPLOADING),
                        org.mockito.ArgumentMatchers.eq("livekit-egress:"),
                        org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(java.util.List.of(file));

        assertThat(service.expiredLiveRecordingIds()).containsExactly(file.getFileId());
    }

    @Test
    void stopsExpiredLiveRecording() {
        MediaFile file = recording();
        file.setSourceFileId("livekit-egress:vs-1:1");
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(videoRepository.findById(file.getFileId())).thenReturn(Optional.of(video(file)));
        when(storage.statSize(file.getObjectKey())).thenReturn(2048L);

        service.expireLiveRecording(file.getFileId());

        verify(egressService).stop("EG_missing");
        assertThat(file.getStatus()).isEqualTo(FileStatus.PROCESSING);
        assertThat(file.getFileSize()).isEqualTo(2048L);
    }

    private MediaFile recording() {
        MediaFile file = new MediaFile();
        file.setFileId("file-1");
        file.setOrgId("org001");
        file.setRobotId("robot-1");
        file.setDeviceId("camera01");
        file.setFileType(FileType.VIDEO);
        file.setFileName("recording.mp4");
        file.setContentType("video/mp4");
        file.setObjectKey("files/org001/robot-1/file-1/original/source.mp4");
        file.setUploadMode(FileUploadMode.MULTIPART);
        file.setStatus(FileStatus.UPLOADING);
        file.setMetadataJson("{\"sessionId\":\"vs-1\",\"startedClientId\":\"web-1\",\"egressId\":\"EG_missing\"}");
        file.setCreatedAt(OffsetDateTime.now());
        file.setUpdatedAt(OffsetDateTime.now());
        return file;
    }

    private CurrentUser user() {
        return new CurrentUser("user-1", "org001", Set.of("MEDIA_OPERATOR"), "web-1");
    }

    private MediaVideoFile video(MediaFile file) {
        MediaVideoFile video = new MediaVideoFile();
        video.setFileId(file.getFileId());
        video.setStatus(VideoFileStatus.PROCESSING);
        video.setStartedAt(file.getCreatedAt());
        return video;
    }
}
