package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.dto.FileDeleteResultResponse;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.model.FileUploadStatus;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.model.MediaFileUpload;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FileServiceDeleteTest {

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
    }

    @Test
    void deletesOwnedFileAndAbortsActiveUpload() {
        MediaFile file = file("file-1", "org001", FileStatus.UPLOADING);
        MediaFileUpload upload = new MediaFileUpload();
        upload.setUploadId("upload-1");
        upload.setStorageUploadId("storage-upload-1");
        upload.setStatus(FileUploadStatus.ACTIVE);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(file));
        when(uploadRepository.findFirstByFileIdAndStatusOrderByCreatedAtDesc("file-1", FileUploadStatus.ACTIVE))
                .thenReturn(Optional.of(upload));
        when(storage.fileRootPrefix(file.getObjectKey())).thenReturn("files/org001/file-1/");

        service.delete(user("org001"), "file-1");

        verify(storage).abortMultipart(file.getObjectKey(), "storage-upload-1");
        verify(storage).deletePrefix("files/org001/file-1/");
        verify(uploadRepository).save(upload);
        verify(fileRepository).save(file);
        assertThat(upload.getStatus()).isEqualTo(FileUploadStatus.ABORTED);
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
    }

    @Test
    void treatsRepeatedDeleteAsSuccess() {
        MediaFile file = file("file-1", "org001", FileStatus.DELETED);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(file));

        service.delete(user("org001"), "file-1");

        verifyNoInteractions(storage);
        verify(uploadRepository, never())
                .findFirstByFileIdAndStatusOrderByCreatedAtDesc("file-1", FileUploadStatus.ACTIVE);
    }

    @Test
    void hidesFileFromAnotherOrganization() {
        MediaFile file = file("file-1", "org002", FileStatus.READY);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.delete(user("org001"), "file-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        verifyNoInteractions(storage);
    }

    @Test
    void returnsPerFileResultsForBatchDelete() {
        MediaFile ownedFile = file("file-1", "org001", FileStatus.READY);
        when(fileRepository.findById("file-1")).thenReturn(Optional.of(ownedFile));
        when(fileRepository.findById("file-missing")).thenReturn(Optional.empty());
        when(storage.fileRootPrefix(ownedFile.getObjectKey())).thenReturn("files/org001/file-1/");

        var response = service.deleteBatch(user("org001"), List.of("file-1", "file-missing"));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.results()).containsExactly(
                new FileDeleteResultResponse("file-1", true, "DELETED", "删除成功"),
                new FileDeleteResultResponse("file-missing", false, "FILE_NOT_FOUND", "未找到文件"));
        assertThat(ownedFile.getStatus()).isEqualTo(FileStatus.DELETED);
    }

    private CurrentUser user(String orgId) {
        return new CurrentUser("user-1", orgId, Set.of("MEDIA_OPERATOR"), "web");
    }

    private MediaFile file(String fileId, String orgId, FileStatus status) {
        MediaFile file = new MediaFile();
        file.setFileId(fileId);
        file.setOrgId(orgId);
        file.setObjectKey("files/" + orgId + "/" + fileId + "/original/test.bin");
        file.setStatus(status);
        return file;
    }
}
