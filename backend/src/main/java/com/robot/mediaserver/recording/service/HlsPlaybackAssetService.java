package com.robot.mediaserver.recording.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.recording.model.MediaRecording;
import com.robot.mediaserver.recording.model.RecordingStatus;
import com.robot.mediaserver.recording.repository.MediaRecordingRepository;
import com.robot.mediaserver.storage.RecordingObjectStorageService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class HlsPlaybackAssetService {

    private final MediaProperties properties;
    private final MediaRecordingRepository repository;
    private final RecordingObjectStorageService storage;
    private final ObjectMapper objectMapper;
    private final RecordingService recordingService;

    public HlsPlaybackAssetService(
            MediaProperties properties,
            MediaRecordingRepository repository,
            RecordingObjectStorageService storage,
            ObjectMapper objectMapper,
            RecordingService recordingService) {
        this.properties = properties;
        this.repository = repository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.recordingService = recordingService;
    }

    @Transactional
    public Optional<String> claimNext() {
        OffsetDateTime timestamp = now();
        List<MediaRecording> candidates = new ArrayList<>(
                repository.findTop10ByStatusOrderByUpdatedAtAsc(RecordingStatus.VERIFYING));
        candidates.addAll(repository.findTop10ByStatusAndProcessingLeaseUntilBeforeOrderByUpdatedAtAsc(
                RecordingStatus.PROCESSING_PLAYBACK,
                timestamp));
        candidates.addAll(repository.findTop10ByStatusAndProcessingLeaseUntilIsNullOrderByUpdatedAtAsc(
                RecordingStatus.PROCESSING_PLAYBACK));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        MediaRecording recording = candidates.get(0);
        recording.setStatus(RecordingStatus.PROCESSING_PLAYBACK);
        recording.setProcessingStartedAt(recording.getProcessingStartedAt() == null
                ? timestamp
                : recording.getProcessingStartedAt());
        recording.setProcessingLeaseUntil(timestamp.plusSeconds(properties.getRecording().getHlsProcessingLeaseSeconds()));
        recording.setUpdatedAt(timestamp);
        repository.save(recording);
        return Optional.of(recording.getRecordingId());
    }

    public void process(String recordingId) {
        MediaRecording recording = repository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));
        Path directory = null;
        try {
            directory = Files.createTempDirectory("recording-hls-");
            Path source = directory.resolve("source.mp4");
            storage.download(recording.getSourceObjectKey(), source);
            ProbeResult probe = probe(source);
            if (!"h264".equals(probe.videoCodec())
                    || (probe.audioCodec() != null && !"aac".equals(probe.audioCodec()))) {
                fail(recordingId, "UNSUPPORTED_MEDIA_CODEC", "Recording must contain H.264 video and AAC or no audio");
                return;
            }
            Path outputDirectory = directory.resolve("hls");
            Files.createDirectories(outputDirectory);
            packageHls(source, outputDirectory);
            List<Path> assets;
            try (var paths = Files.list(outputDirectory)) {
                assets = paths.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            }
            long totalSize = 0;
            int segmentCount = 0;
            String prefix = recordingService.hlsPrefix(recording);
            for (Path asset : assets) {
                String fileName = asset.getFileName().toString();
                storage.uploadFile(prefix + fileName, asset, fileName.endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl"
                        : "video/mp2t");
                totalSize += Files.size(asset);
                if (fileName.endsWith(".ts")) {
                    segmentCount++;
                }
            }
            ready(recordingId, probe, prefix + "index.m3u8", segmentCount, totalSize);
        } catch (Exception ex) {
            fail(recordingId, "HLS_PROCESSING_FAILED", ex.getMessage());
        } finally {
            deleteDirectory(directory);
        }
    }

    @Transactional
    public void ready(String recordingId, ProbeResult probe, String playlistKey, int segmentCount, long totalSize) {
        MediaRecording recording = repository.findById(recordingId).orElseThrow();
        recording.setVideoCodec(probe.videoCodec());
        recording.setAudioCodec(probe.audioCodec());
        recording.setDurationSeconds((int) Math.ceil(probe.durationSeconds()));
        recording.setHlsPlaylistObjectKey(playlistKey);
        recording.setHlsSegmentCount(segmentCount);
        recording.setHlsTotalSize(totalSize);
        recording.setStatus(RecordingStatus.READY);
        recording.setProcessingCompletedAt(now());
        recording.setProcessingLeaseUntil(null);
        recording.setErrorCode(null);
        recording.setErrorMessage(null);
        recording.setUpdatedAt(now());
        repository.save(recording);
    }

    @Transactional
    public void fail(String recordingId, String errorCode, String message) {
        MediaRecording recording = repository.findById(recordingId).orElseThrow();
        recording.setStatus(RecordingStatus.FAILED);
        recording.setErrorCode(errorCode);
        recording.setErrorMessage(message == null ? "HLS processing failed" : truncate(message));
        recording.setProcessingCompletedAt(now());
        recording.setProcessingLeaseUntil(null);
        recording.setUpdatedAt(now());
        repository.save(recording);
    }

    private ProbeResult probe(Path source) throws Exception {
        Process process = new ProcessBuilder(
                properties.getRecording().getFfprobePath(),
                "-v", "error",
                "-show_entries", "stream=codec_type,codec_name:format=duration",
                "-of", "json",
                source.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("ffprobe could not read uploaded MP4");
        }
        JsonNode root = objectMapper.readTree(output);
        String videoCodec = null;
        String audioCodec = null;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText())) {
                videoCodec = stream.path("codec_name").asText(null);
            } else if ("audio".equals(stream.path("codec_type").asText())) {
                audioCodec = stream.path("codec_name").asText(null);
            }
        }
        double duration = root.path("format").path("duration").asDouble(0);
        if (videoCodec == null || duration <= 0) {
            throw new IllegalStateException("Uploaded MP4 has no readable video duration");
        }
        return new ProbeResult(videoCodec, audioCodec, duration);
    }

    private void packageHls(Path source, Path outputDirectory) throws Exception {
        Process process = new ProcessBuilder(
                properties.getRecording().getHlsFfmpegPath(),
                "-y",
                "-i", source.toString(),
                "-c", "copy",
                "-hls_time", String.valueOf(properties.getRecording().getHlsSegmentDurationSeconds()),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outputDirectory.resolve("segment_%06d.ts").toString(),
                outputDirectory.resolve("index.m3u8").toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (process.waitFor() != 0 || !Files.exists(outputDirectory.resolve("index.m3u8"))) {
            throw new IllegalStateException("ffmpeg failed to generate HLS playback asset");
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary processing files are best-effort cleanup only.
                }
            });
        } catch (IOException ignored) {
            // Temporary processing files are best-effort cleanup only.
        }
    }

    private String truncate(String message) {
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public record ProbeResult(String videoCodec, String audioCodec, double durationSeconds) {
    }
}
