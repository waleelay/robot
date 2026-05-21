package com.robot.mediaserver.video.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.source.service.MediaSourceService;
import com.robot.mediaserver.video.dto.FailSnapshotRequest;
import com.robot.mediaserver.video.model.MediaSnapshot;
import com.robot.mediaserver.video.model.SnapshotStatus;
import com.robot.mediaserver.video.repository.MediaSnapshotRepository;
import com.robot.mediaserver.video.service.SnapshotService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnapshotWorkerScheduler {

    private final MediaProperties properties;
    private final MediaSnapshotRepository repository;
    private final MediaSourceService mediaSourceService;
    private final SnapshotService snapshotService;

    public SnapshotWorkerScheduler(
            MediaProperties properties,
            MediaSnapshotRepository repository,
            MediaSourceService mediaSourceService,
            SnapshotService snapshotService) {
        this.properties = properties;
        this.repository = repository;
        this.mediaSourceService = mediaSourceService;
        this.snapshotService = snapshotService;
    }

    @Scheduled(fixedDelayString = "${media.snapshot-worker.fixed-delay-ms:3000}")
    public void capturePending() {
        if (!properties.getSnapshotWorker().isEnabled()) {
            return;
        }
        repository.findTop10ByStatusOrderByCreatedAtAsc(SnapshotStatus.PROCESSING)
                .forEach(this::capture);
    }

    private void capture(MediaSnapshot snapshot) {
        try {
            String rtspUrl = mediaSourceService.rtspUrl(
                    snapshot.getRobotId(),
                    snapshot.getDeviceId(),
                    snapshot.getChannel(),
                    snapshot.getQuality());
            byte[] image = ffmpeg(rtspUrl);
            snapshotService.completeBytes(snapshot.getSnapshotId(), image, OffsetDateTime.now(ZoneOffset.UTC));
        } catch (Exception ex) {
            FailSnapshotRequest request = new FailSnapshotRequest();
            request.setErrorCode("SNAPSHOT_CAPTURE_FAILED");
            request.setErrorMessage(ex.getMessage());
            snapshotService.fail(snapshot.getSnapshotId(), request);
        }
    }

    private byte[] ffmpeg(String rtspUrl) throws Exception {
        Path file = Files.createTempFile("snapshot-", ".jpg");
        Process process = new ProcessBuilder(
                properties.getSnapshotWorker().getFfmpegPath(),
                "-rtsp_transport",
                "tcp",
                "-y",
                "-i",
                rtspUrl,
                "-frames:v",
                "1",
                file.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        boolean finished = process.waitFor(properties.getSnapshotWorker().getTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(file);
            throw new IllegalStateException("ffmpeg timeout");
        }
        byte[] bytes = Files.readAllBytes(file);
        Files.deleteIfExists(file);
        if (process.exitValue() != 0 || bytes.length == 0) {
            throw new IllegalStateException("ffmpeg capture failed");
        }
        return bytes;
    }
}
