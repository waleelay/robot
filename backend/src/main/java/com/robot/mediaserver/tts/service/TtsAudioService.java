package com.robot.mediaserver.tts.service;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TtsAudioService {

    private final Object openTtsLock = new Object();
    private final MediaProperties properties;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final RestTemplate ttsHttp;
    private final Path outputRoot;
    private final Map<Path, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();

    public TtsAudioService(MediaProperties properties, MediaWebSocketPublisher webSocketPublisher) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTts().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getTts().getReadTimeoutMs());
        this.ttsHttp = new RestTemplate(factory);
        this.outputRoot = Path.of(properties.getTts().getOutputRoot()).toAbsolutePath().normalize();
    }

    public ResponseEntity<FileSystemResource> generateAndReturnFile(String robotId, String text) {
        GeneratedTts generated = generate(robotId, text);
        ReentrantReadWriteLock.ReadLock readLock = lockFor(generated.file()).readLock();
        readLock.lock();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType(generated.format()));
            headers.setContentLength(Files.size(generated.file()));
            headers.setContentDispositionFormData("file", generated.file().getFileName().toString());
            headers.add("X-TTS-Cache-Hit", String.valueOf(generated.cacheHit()));
            return new ResponseEntity<>(new FileSystemResource(generated.file()), headers, HttpStatus.OK);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 TTS 文件失败", ex);
        } finally {
            readLock.unlock();
        }
    }

    public void generateAndPublishToFrontend(String robotId, String text) {
        GeneratedTts generated = generate(robotId, text);
        AudioInputStream audioInputStream = null;
        ReentrantReadWriteLock.ReadLock readLock = lockFor(generated.file()).readLock();
        boolean locked = false;
        try {
            locked = readLock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("获取 TTS 读取锁超时");
            }
            File file = generated.file().toFile();
            if (!file.exists()) {
                throw new IllegalStateException("TTS 文件不存在");
            }
            audioInputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat audioFormat = audioInputStream.getFormat();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("robotId", robotId);
            meta.put("filename", generated.file().getFileName().toString());
            meta.put("format", generated.format());
            meta.put("sampleRate", audioFormat.getSampleRate());
            meta.put("channels", audioFormat.getChannels());
            meta.put("bitsPerSample", audioFormat.getSampleSizeInBits());
            meta.put("encoding", audioFormat.getEncoding().toString());
            meta.put("cacheHit", generated.cacheHit());
            webSocketPublisher.publish("tts.audio.meta", meta);

            byte[] raw = Files.readAllBytes(generated.file());
            if (raw.length > properties.getTts().getWavHeaderOffset() && isWav(raw)) {
                raw = java.util.Arrays.copyOfRange(raw, properties.getTts().getWavHeaderOffset(), raw.length);
            }
            webSocketPublisher.publishBinary(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("发布 TTS 音频到前端失败", ex);
        } finally {
            if (audioInputStream != null) {
                try {
                    audioInputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (locked) {
                readLock.unlock();
            }
        }
    }

    private GeneratedTts generate(String robotId, String inputText) {
        if (!properties.getTts().isEnabled()) {
            throw new IllegalStateException("TTS 未启用");
        }
        String text = normalizeText(inputText);
        if (text.isBlank()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        if (text.length() > properties.getTts().getMaxTextLength()) {
            throw new IllegalArgumentException("文本长度不能超过 " + properties.getTts().getMaxTextLength());
        }

        String voice = properties.getTts().getVoice();
        String format = normalizeFormat(properties.getTts().getFormat());
        String hash = sha256(text + "\n" + voice + "\n" + format);
        Path file = outputFile(robotId, hash, format);

        ReentrantReadWriteLock.WriteLock writeLock = lockFor(file).writeLock();
        boolean locked = false;
        try {
            locked = writeLock.tryLock(properties.getTts().getGenerateLockTimeoutSeconds(), TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("获取 TTS 文件写入锁超时");
            }
            Files.createDirectories(Objects.requireNonNull(file.getParent()));
            boolean cacheHit = Files.exists(file) && Files.size(file) > 0;
            if (!cacheHit) {
                Path tmp = file.resolveSibling("." + file.getFileName() + ".tmp");
                try {
                    synchronized (openTtsLock) {
                        requestOpenTts(text, voice, format, tmp);
                    }
                    if (!Files.exists(tmp) || Files.size(tmp) == 0) {
                        throw new IllegalStateException("OpenTTS 生成了空文件");
                    }
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            return new GeneratedTts(file, format, cacheHit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TTS 生成被中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("写入 TTS 文件失败", ex);
        } finally {
            if (locked) {
                writeLock.unlock();
            }
        }
    }

    private void requestOpenTts(String text, String voice, String format, Path outputFile) {
        String url = UriComponentsBuilder.fromUriString(properties.getTts().getEngineUrl())
                .queryParam("text", text)
                .queryParam("voice", voice)
                .queryParam("format", format)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        ResponseEntity<byte[]> response = ttsHttp.exchange(URI.create(url), HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
            throw new IllegalStateException("OpenTTS 调用失败，状态码=" + response.getStatusCode());
        }
        try {
            Files.write(outputFile, response.getBody());
        } catch (IOException ex) {
            throw new IllegalStateException("写入 OpenTTS 响应失败", ex);
        }
    }

    private Path outputFile(String robotId, String hash, String format) {
        String filename = hash.substring(0, 24) + "." + format;
        Path dir = outputRoot.resolve(safeSegment(valueOrDefault(robotId, "unknown"))).normalize();
        Path file = dir.resolve(filename).normalize();
        if (!file.startsWith(outputRoot)) {
            throw new IllegalArgumentException("输出路径必须位于 TTS 输出根目录下");
        }
        return file;
    }

    private ReentrantReadWriteLock lockFor(Path file) {
        return fileLocks.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new ReentrantReadWriteLock());
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("[！!。?？]", "");
    }

    private static String normalizeFormat(String format) {
        String value = valueOrDefault(format, "wav").toLowerCase();
        if (!value.matches("[a-z0-9]{1,8}")) {
            throw new IllegalArgumentException("无效的 TTS 格式");
        }
        return value;
    }

    private static String safeSegment(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static MediaType contentType(String format) {
        if ("wav".equals(format)) {
            return MediaType.parseMediaType("audio/wav");
        }
        if ("mp3".equals(format)) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static boolean isWav(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
    }

    private record GeneratedTts(Path file, String format, boolean cacheHit) {
    }
}
