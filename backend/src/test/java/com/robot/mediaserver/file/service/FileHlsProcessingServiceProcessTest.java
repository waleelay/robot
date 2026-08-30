package com.robot.mediaserver.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHlsProcessingServiceProcessTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsOutputWhileWaitingForNormalExit() throws Exception {
        var result = service().runProcess(
                List.of("/bin/sh", "-c", "printf 'ok'"),
                2,
                "TEST");

        assertThat(result.output()).isEqualTo("ok");
    }

    @Test
    void limitsCapturedOutput() {
        assertThatThrownBy(() -> service().runProcess(
                List.of("/bin/sh", "-c", "yes x | head -c 100000; exit 7"),
                2,
                "TEST"))
                .isInstanceOf(FileHlsProcessingService.ProcessExecutionException.class)
                .hasMessageStartingWith("子进程执行失败：")
                .satisfies(error -> assertThat(error.getMessage().length()).isLessThan(600));
    }

    @Test
    void killsTimedOutProcess() throws Exception {
        Path pidFile = tempDirectory.resolve("process.pid");

        assertThatThrownBy(() -> service().runProcess(
                List.of(
                        "/bin/sh",
                        "-c",
                        "echo $$ > \"$1\"; trap '' TERM; while :; do sleep 1; done",
                        "test-process",
                        pidFile.toString()),
                1,
                "TEST"))
                .isInstanceOf(FileHlsProcessingService.ProcessExecutionException.class)
                .extracting(error -> ((FileHlsProcessingService.ProcessExecutionException) error).code())
                .isEqualTo("TEST_TIMEOUT");

        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    private FileHlsProcessingService service() {
        return new FileHlsProcessingService(
                new MediaProperties(),
                mock(MediaFileRepository.class),
                mock(FileObjectStorageService.class),
                mock(FileService.class),
                new ObjectMapper());
    }
}
