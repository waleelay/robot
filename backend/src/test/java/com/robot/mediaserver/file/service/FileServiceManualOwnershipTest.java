package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.model.FileUploadMode;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class FileServiceManualOwnershipTest {

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
        when(storage.buildObjectKey(any(), any(), any(), any(), any())).thenReturn("files/manual.jpg");
    }

    @Test
    void assignsSnapshotToCurrentUser() {
        service.uploadSimple(
                operator("user-1"),
                new MockMultipartFile("file", "snapshot.jpg", "image/jpeg", new byte[] {1}),
                FileType.IMAGE,
                "robot-1",
                "camera01",
                null,
                "snapshot-1",
                "{\"source\":\"WEB_SNAPSHOT\"}");

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(fileRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("user-1");
    }

    @Test
    void rejectsSnapshotWithoutOperatorRole() {
        assertThatThrownBy(() -> service.uploadSimple(
                        viewer("user-1"),
                        new MockMultipartFile("file", "snapshot.jpg", "image/jpeg", new byte[] {1}),
                        FileType.IMAGE,
                        "robot-1",
                        "camera01",
                        null,
                        "snapshot-1",
                        "{\"source\":\"WEB_SNAPSHOT\"}"))
                .isInstanceOf(FileApiException.class)
                .satisfies(ex -> {
                    FileApiException apiException = (FileApiException) ex;
                    assertThat(apiException.getStatus().value()).isEqualTo(403);
                    assertThat(apiException.getCode()).isEqualTo("MEDIA_PERMISSION_DENIED");
                });

        verifyNoInteractions(fileRepository);
    }

    @Test
    void hidesOwnedDetailFromAnotherUser() {
        MediaFile file = file("user-1");
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.detail(viewer("user-2"), file.getFileId()))
                .isInstanceOf(FileApiException.class)
                .satisfies(ex -> assertThat(((FileApiException) ex).getCode()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void keepsUnownedBusinessFileSharedWithinOrganization() {
        MediaFile file = file(null);
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));

        assertThat(service.detail(new CurrentUser("user-2", "org001", Set.of(), "web"), file.getFileId()).fileId())
                .isEqualTo(file.getFileId());
    }

    @Test
    void rejectsOversizedProxyContentBeforeReadingObject() {
        MediaFile file = file("user-1");
        when(fileRepository.findById(file.getFileId())).thenReturn(Optional.of(file));
        when(storage.statSize(file.getObjectKey())).thenReturn(32L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.content(viewer("user-1"), file.getFileId()))
                .isInstanceOf(FileApiException.class)
                .satisfies(ex -> {
                    FileApiException apiException = (FileApiException) ex;
                    assertThat(apiException.getStatus().value()).isEqualTo(413);
                    assertThat(apiException.getCode()).isEqualTo("FILE_CONTENT_TOO_LARGE");
                });

        verify(storage, org.mockito.Mockito.never()).readObject(any());
    }

    private CurrentUser operator(String userId) {
        return new CurrentUser(userId, "org001", Set.of("MEDIA_OPERATOR"), "web-1");
    }

    private CurrentUser viewer(String userId) {
        return new CurrentUser(userId, "org001", Set.of("MEDIA_VIEWER"), "web-1");
    }

    private MediaFile file(String createdBy) {
        MediaFile file = new MediaFile();
        file.setFileId("file-1");
        file.setOrgId("org001");
        file.setCreatedBy(createdBy);
        file.setFileType(FileType.IMAGE);
        file.setFileName("snapshot.jpg");
        file.setContentType("image/jpeg");
        file.setObjectKey("files/manual.jpg");
        file.setUploadMode(FileUploadMode.SIMPLE);
        file.setStatus(FileStatus.READY);
        file.setCreatedAt(OffsetDateTime.now());
        file.setUpdatedAt(OffsetDateTime.now());
        return file;
    }
}
