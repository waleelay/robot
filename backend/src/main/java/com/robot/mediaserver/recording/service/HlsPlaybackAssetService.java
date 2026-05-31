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
import java.nio.charset.StandardCharsets;
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
            Path outputDirectory = directory.resolve("hls");
            Files.createDirectories(outputDirectory);
            packageHls(source, outputDirectory, canCopyToHls(probe));
            List<Path> assets;
            try (var paths = Files.list(outputDirectory)) {
                assets = paths.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            }
            long totalSize = 0;
            int segmentCount = 0;
            String prefix = recordingService.hlsPrefix(recording);
            for (Path asset : assets) {
                String fileName = asset.getFileName().toString();
                storage.uploadFile(prefix + fileName, asset, contentType(fileName));
                totalSize += Files.size(asset);
                if (fileName.endsWith(".ts") || fileName.endsWith(".m4s")) {
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
                "-show_entries", "stream=codec_type,codec_name,width,height,pix_fmt,level:format=duration",
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
        String pixelFormat = null;
        Integer width = null;
        Integer height = null;
        Integer level = null;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText())) {
                videoCodec = stream.path("codec_name").asText(null);
                pixelFormat = stream.path("pix_fmt").asText(null);
                width = stream.path("width").isMissingNode() ? null : stream.path("width").asInt();
                height = stream.path("height").isMissingNode() ? null : stream.path("height").asInt();
                level = stream.path("level").isMissingNode() ? null : stream.path("level").asInt();
            } else if ("audio".equals(stream.path("codec_type").asText())) {
                audioCodec = stream.path("codec_name").asText(null);
            }
        }
        double duration = root.path("format").path("duration").asDouble(0);
        if (videoCodec == null || duration <= 0) {
            throw new IllegalStateException("Uploaded MP4 has no readable video duration");
        }
        return new ProbeResult(videoCodec, audioCodec, pixelFormat, width, height, level, duration);
    }

    private boolean canCopyToHls(ProbeResult probe) {
        return "h264".equals(probe.videoCodec())
                && (probe.audioCodec() == null || "aac".equals(probe.audioCodec()))
                && "yuv420p".equals(probe.pixelFormat())
                && probe.width() != null
                && probe.height() != null
                && probe.width() <= 1920
                && probe.height() <= 1080
                && (probe.level() == null || probe.level() <= 42);
    }

    private void packageHls(Path source, Path outputDirectory, boolean copyCodecs) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(properties.getRecording().getHlsFfmpegPath());
        command.add("-y");
        command.add("-i");
        command.add(source.toString());
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a:0?");
        command.add("-dn");
        command.add("-sn");
        if (copyCodecs) {
            command.add("-c");
            command.add("copy");
        } else {
            command.add("-c:v");
            command.add("libx264");
            command.add("-preset");
            command.add("veryfast");
            command.add("-profile:v");
            command.add("high");
            command.add("-level:v");
            command.add("4.2");
            command.add("-crf");
            command.add("23");
            command.add("-vf");
            command.add("scale=min(1920\\,iw):min(1080\\,ih):force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p");
            command.add("-sc_threshold");
            command.add("0");
            command.add("-force_key_frames");
            command.add("expr:gte(t,n_forced*" + properties.getRecording().getHlsSegmentDurationSeconds() + ")");
            command.add("-maxrate");
            command.add("6500000");
            command.add("-bufsize");
            command.add("13000000");
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
            command.add("-ac");
            command.add("2");
        }
        command.add("-hls_time");
        command.add(String.valueOf(properties.getRecording().getHlsSegmentDurationSeconds()));
        command.add("-hls_playlist_type");
        command.add("vod");
        command.add("-hls_flags");
        command.add("independent_segments");
        command.add("-hls_segment_type");
        command.add("fmp4");
        command.add("-hls_fmp4_init_filename");
        command.add("init.mp4");
        command.add("-hls_segment_filename");
        command.add(outputDirectory.resolve("segment_%06d.m4s").toString());
        command.add(outputDirectory.resolve("index.m3u8").toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0 || !Files.exists(outputDirectory.resolve("index.m3u8"))) {
            throw new IllegalStateException("ffmpeg failed to generate HLS playback asset: " + truncate(output));
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

    private String contentType(String fileName) {
        if (fileName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (fileName.endsWith(".m4s") || fileName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (fileName.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    public record ProbeResult(
            String videoCodec,
            String audioCodec,
            String pixelFormat,
            Integer width,
            Integer height,
            Integer level,
            double durationSeconds) {
    }
}
