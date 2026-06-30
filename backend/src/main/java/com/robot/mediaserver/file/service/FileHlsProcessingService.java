package com.robot.mediaserver.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.model.FileStatus;
import com.robot.mediaserver.file.model.FileType;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import jakarta.transaction.Transactional;
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
public class FileHlsProcessingService {

    private final MediaProperties properties;
    private final MediaFileRepository fileRepository;
    private final FileObjectStorageService storage;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public FileHlsProcessingService(
            MediaProperties properties,
            MediaFileRepository fileRepository,
            FileObjectStorageService storage,
            FileService fileService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<String> claimNext() {
        List<MediaFile> candidates = fileRepository.findTop10ByFileTypeAndStatusOrderByUpdatedAtAsc(FileType.VIDEO, FileStatus.PROCESSING);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        MediaFile file = candidates.get(0);
        file.setUpdatedAt(now());
        fileRepository.save(file);
        return Optional.of(file.getFileId());
    }

    public void process(String fileId) {
        MediaFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("未找到文件：" + fileId));
        Path directory = null;
        try {
            Long sourceSize = sourceSize(file);
            if (sourceSize == null) {
                return;
            }
            directory = Files.createTempDirectory("file-hls-");
            Path source = directory.resolve("source.mp4");
            storage.download(file.getObjectKey(), source);
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
            String prefix = storage.hlsPrefix(file.getObjectKey());
            for (Path asset : assets) {
                String fileName = asset.getFileName().toString();
                storage.uploadFile(prefix + fileName, asset, contentType(fileName));
                totalSize += Files.size(asset);
                if (fileName.endsWith(".ts") || fileName.endsWith(".m4s")) {
                    segmentCount++;
                }
            }
            fileService.markVideoReady(
                    fileId,
                    new FileService.VideoProbeResult(
                            probe.videoCodec(),
                            probe.audioCodec(),
                            probe.width(),
                            probe.height(),
                            probe.durationSeconds()),
                    prefix + "index.m3u8",
                    segmentCount,
                    totalSize,
                    sourceSize);
        } catch (Exception ex) {
            fileService.markVideoFailed(fileId, "HLS_PROCESSING_FAILED", ex.getMessage());
        } finally {
            deleteDirectory(directory);
        }
    }

    private Long sourceSize(MediaFile file) {
        try {
            long sourceSize = storage.statSize(file.getObjectKey());
            if (sourceSize > 0) {
                return sourceSize;
            }
            if (isLiveKitEgress(file)) {
                return null;
            }
            throw new IllegalStateException("视频源对象为空：" + file.getObjectKey());
        } catch (RuntimeException ex) {
            if (isLiveKitEgress(file)) {
                return null;
            }
            throw ex;
        }
    }

    private boolean isLiveKitEgress(MediaFile file) {
        String metadata = file.getMetadataJson();
        return (file.getSourceFileId() != null && file.getSourceFileId().startsWith("livekit-egress:"))
                || (metadata != null && metadata.contains("LIVEKIT_EGRESS"));
    }

    private ProbeResult probe(Path source) throws Exception {
        Process process = new ProcessBuilder(
                properties.getFile().getFfprobePath(),
                "-v", "error",
                "-show_entries", "stream=codec_type,codec_name,width,height,pix_fmt,level:format=duration",
                "-of", "json",
                source.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("ffprobe 无法读取已上传的视频");
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
            throw new IllegalStateException("已上传的视频没有可读取的视频时长");
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
        command.add(properties.getFile().getHlsFfmpegPath());
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
            command.add("expr:gte(t,n_forced*" + properties.getFile().getHlsSegmentDurationSeconds() + ")");
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
        command.add(String.valueOf(properties.getFile().getHlsSegmentDurationSeconds()));
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

    private void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private String truncate(String message) {
        return message == null ? "" : message.length() > 500 ? message.substring(0, 500) : message;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record ProbeResult(
            String videoCodec,
            String audioCodec,
            String pixelFormat,
            Integer width,
            Integer height,
            Integer level,
            double durationSeconds) {
    }
}
