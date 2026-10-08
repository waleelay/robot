package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import com.robot.mediaserver.file.repository.MediaFileUploadRepository;
import com.robot.mediaserver.file.repository.MediaVideoFileRepository;
import com.robot.mediaserver.livekit.LiveKitEgressService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 验证简单上传成功或存储失败时均释放调用方取得的输入流。 */
class FileServiceUploadStreamTest {
    @Test
    void closesUploadStreamOnSuccess() throws Exception {
        verifyClosure(false);
    }

    @Test
    void closesUploadStreamAndPreservesStorageFailure() throws Exception {
        verifyClosure(true);
    }

    private void verifyClosure(boolean failure) throws Exception {
        InputStream input = spy(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        FileObjectStorageService storage = mock(FileObjectStorageService.class);
        when(storage.buildObjectKey(any(), any(), any(), any(), any())).thenReturn("objects/file-1");
        FileService service = new FileService(new MediaProperties(), mock(MediaFileRepository.class),
                mock(MediaFileUploadRepository.class), mock(MediaVideoFileRepository.class), storage,
                mock(LiveKitEgressService.class), new ObjectMapper(), mock(FileSourceLockService.class));
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3}) {
            @Override public InputStream getInputStream() { return input; }
        };
        if (failure) {
            FileStorageException error = new FileStorageException("storage unavailable", new IllegalStateException());
            doThrow(error).when(storage).upload(any(), any(), anyLong(), any());
            assertThatThrownBy(() -> service.uploadSimple(null, file, FileType.IMAGE, null, null, null, null, null))
                    .isSameAs(error);
        } else {
            assertThat(service.uploadSimple(null, file, FileType.IMAGE, null, null, null, null, null).status()).isEqualTo("READY");
        }
        verify(input).close();
    }
}
