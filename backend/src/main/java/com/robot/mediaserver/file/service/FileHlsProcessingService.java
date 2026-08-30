package com.robot.mediaserver.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.media.common.file.FileStatus;
import com.robot.media.common.file.FileType;
import com.robot.mediaserver.file.model.MediaFile;
import com.robot.mediaserver.file.repository.MediaFileRepository;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FileHlsProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FileHlsProcessingService.class);
    private static final int PROCESS_OUTPUT_LIMIT_BYTES = 64 * 1024;
    private static final long PROCESS_STOP_WAIT_SECONDS = 2;

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
            packageHls(source, outputDirectory, canCopyToHls(probe), probe.durationSeconds());
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
        } catch (ProcessExecutionException ex) {
            log.warn("HLS 处理子进程失败，文件={}，错误码={}，原因={}", fileId, ex.code(), ex.getMessage());
            fileService.markVideoFailed(fileId, ex.code(), ex.getMessage());
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
        ProcessResult result = runProcess(List.of(
                properties.getFile().getFfprobePath(),
                "-v", "error",
                "-show_entries", "stream=codec_type,codec_name,width,height,pix_fmt,level:format=duration",
                "-of", "json",
                source.toString()), 60, "FFPROBE");
        String output = result.output();
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

    private void packageHls(
            Path source,
            Path outputDirectory,
            boolean copyCodecs,
            double durationSeconds) throws Exception {
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
        long timeoutSeconds = Math.max(
                Math.max(1, properties.getFile().getHlsProcessingLeaseSeconds()),
                (long) Math.ceil(durationSeconds * 2));
        ProcessResult result = runProcess(command, timeoutSeconds, "FFMPEG");
        if (!Files.exists(outputDirectory.resolve("index.m3u8"))) {
            throw new ProcessExecutionException(
                    "FFMPEG_OUTPUT_MISSING",
                    "ffmpeg 未生成 HLS 播放清单：" + truncate(result.output()));
        }
    }

    ProcessResult runProcess(List<String> command, long timeoutSeconds, String codePrefix) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread outputReader = new Thread(
                () -> drainProcessOutput(process.getInputStream(), output, readFailure),
                "media-process-output-" + process.pid());
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            stopProcessTree(process);
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException(codePrefix + "_INTERRUPTED", "子进程等待被中断");
        }
        if (!finished) {
            stopProcessTree(process);
            joinOutputReader(outputReader);
            throw new ProcessExecutionException(codePrefix + "_TIMEOUT", "子进程超过允许执行时间");
        }
        joinOutputReader(outputReader);
        if (readFailure.get() != null) {
            throw new ProcessExecutionException(codePrefix + "_OUTPUT_READ_FAILED", "读取子进程输出失败");
        }
        String text = output.toString(StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new ProcessExecutionException(codePrefix + "_FAILED", "子进程执行失败：" + truncate(text));
        }
        return new ProcessResult(text);
    }

    private void drainProcessOutput(
            InputStream input,
            ByteArrayOutputStream output,
            AtomicReference<IOException> readFailure) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                int remaining = PROCESS_OUTPUT_LIMIT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(read, remaining));
                }
            }
        } catch (IOException ex) {
            readFailure.set(ex);
        }
    }

    private void joinOutputReader(Thread outputReader) throws InterruptedException {
        outputReader.join(TimeUnit.SECONDS.toMillis(PROCESS_STOP_WAIT_SECONDS));
        if (outputReader.isAlive()) {
            outputReader.interrupt();
        }
    }

    private void stopProcessTree(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (process.waitFor(PROCESS_STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                return;
            }
            descendants.forEach(handle -> {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
            process.destroyForcibly();
            process.waitFor(PROCESS_STOP_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
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

    record ProcessResult(String output) {
    }

    static final class ProcessExecutionException extends Exception {
        private final String code;

        ProcessExecutionException(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
