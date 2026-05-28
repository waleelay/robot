package com.robot.mediaserver.recording.service;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.recording.api.RecordingApiException;
import com.robot.mediaserver.recording.dto.CreateRecordingUploadRequest;
import com.robot.mediaserver.recording.dto.PartInfoResponse;
import com.robot.mediaserver.recording.dto.PartUploadUrlResponse;
import com.robot.mediaserver.recording.dto.PartUrlsResponse;
import com.robot.mediaserver.recording.dto.PlaybackUrlResponse;
import com.robot.mediaserver.recording.dto.RecordingListItemResponse;
import com.robot.mediaserver.recording.dto.RecordingListResponse;
import com.robot.mediaserver.recording.dto.RecordingStatusResponse;
import com.robot.mediaserver.recording.dto.RecordingUploadResponse;
import com.robot.mediaserver.recording.model.MediaRecording;
import com.robot.mediaserver.recording.model.MediaRecordingUpload;
import com.robot.mediaserver.recording.model.RecordingStatus;
import com.robot.mediaserver.recording.model.UploadStatus;
import com.robot.mediaserver.recording.repository.MediaRecordingRepository;
import com.robot.mediaserver.recording.repository.MediaRecordingUploadRepository;
import com.robot.mediaserver.storage.RecordingObjectStorageService;
import com.robot.mediaserver.storage.RecordingObjectStorageService.StoredPart;
import jakarta.transaction.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecordingService {

    private final MediaProperties properties;
    private final MediaRecordingRepository recordingRepository;
    private final MediaRecordingUploadRepository uploadRepository;
    private final RecordingObjectStorageService storage;

    public RecordingService(
            MediaProperties properties,
            MediaRecordingRepository recordingRepository,
            MediaRecordingUploadRepository uploadRepository,
            RecordingObjectStorageService storage) {
        this.properties = properties;
        this.recordingRepository = recordingRepository;
        this.uploadRepository = uploadRepository;
        this.storage = storage;
    }

    @Transactional
    public RecordingUploadResponse createOrResume(String robotId, CreateRecordingUploadRequest request) {
        requireRobotId(robotId);
        validateSource(request);
        MediaRecording recording = recordingRepository.findByRobotIdAndSourceFileId(robotId, request.getSourceFileId())
                .orElse(null);
        if (recording != null) {
            ensureSameSource(recording, request);
            if (recording.getStatus() == RecordingStatus.READY
                    || recording.getStatus() == RecordingStatus.VERIFYING
                    || recording.getStatus() == RecordingStatus.PROCESSING_PLAYBACK) {
                return completedResponse(recording);
            }
            MediaRecordingUpload active = uploadRepository
                    .findFirstByRecordingIdAndStatusOrderByCreatedAtDesc(recording.getRecordingId(), UploadStatus.ACTIVE)
                    .orElse(null);
            if (active != null && active.getExpiresAt().isAfter(now())) {
                refresh(active);
                return uploadResponse(recording, active, true);
            }
        } else {
            recording = newRecording(robotId, request);
            recordingRepository.save(recording);
        }
        checkQuota(robotId);
        MediaRecordingUpload upload = newUpload(recording);
        recording.setStatus(RecordingStatus.UPLOADING);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
        uploadRepository.save(upload);
        return uploadResponse(recording, upload, true);
    }

    @Transactional
    public PartUrlsResponse partUrls(String robotId, String uploadId, List<Integer> partNumbers) {
        MediaRecordingUpload upload = requireActiveUpload(robotId, uploadId);
        MediaRecording recording = requireRecording(upload.getRecordingId());
        if (partNumbers.size() > 2) {
            throw error(HttpStatus.BAD_REQUEST, "TOO_MANY_PART_URLS", "At most two part URLs may be requested at once");
        }
        for (Integer part : partNumbers) {
            if (part == null || part < 1 || part > upload.getPartCount()) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_PART_NUMBER", "Invalid part number");
            }
        }
        refresh(upload);
        List<PartUploadUrlResponse> urls = partNumbers.stream()
                .distinct()
                .map(number -> new PartUploadUrlResponse(
                        number,
                        storage.presignUploadPart(recording.getSourceObjectKey(), upload.getStorageUploadId(), number)))
                .toList();
        return new PartUrlsResponse(now().plusSeconds(properties.getRecording().getUploadUrlTtlSeconds()), urls);
    }

    @Transactional
    public RecordingStatusResponse complete(String robotId, String uploadId) {
        MediaRecordingUpload upload = requireUpload(robotId, uploadId);
        MediaRecording recording = requireRecording(upload.getRecordingId());
        if (upload.getStatus() == UploadStatus.COMPLETED) {
            return status(recording);
        }
        List<StoredPart> parts = storage.listParts(recording.getSourceObjectKey(), upload.getStorageUploadId());
        validateUploadedParts(recording, upload, parts);
        storage.completeMultipart(recording.getSourceObjectKey(), upload.getStorageUploadId(), parts);
        long storedSize = storage.statSize(recording.getSourceObjectKey());
        if (storedSize != recording.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "SOURCE_SIZE_MISMATCH", "Completed object size does not match registered file");
        }
        OffsetDateTime timestamp = now();
        upload.setStatus(UploadStatus.COMPLETED);
        upload.setCompletedAt(timestamp);
        upload.setLastActiveAt(timestamp);
        recording.setStatus(RecordingStatus.VERIFYING);
        recording.setUploadedAt(timestamp);
        recording.setUpdatedAt(timestamp);
        uploadRepository.save(upload);
        recordingRepository.save(recording);
        return status(recording);
    }

    public RecordingStatusResponse robotStatus(String robotId, String recordingId) {
        MediaRecording recording = requireRobotRecording(robotId, recordingId);
        return status(recording);
    }

    public RecordingListResponse list(
            CurrentUser user,
            String robotId,
            String deviceId,
            RecordingStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {
        Specification<MediaRecording> spec = (root, query, cb) -> cb.equal(root.get("orgId"), user.orgId());
        if (robotId != null && !robotId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("robotId"), robotId));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deviceId"), deviceId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("recordedStartedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("recordedStartedAt"), to));
        }
        Page<MediaRecording> result = recordingRepository.findAll(
                spec,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new RecordingListResponse(result.stream().map(this::item).toList(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public PlaybackUrlResponse playUrl(CurrentUser user, String recordingId) {
        MediaRecording recording = requireRecording(recordingId);
        if (!Objects.equals(recording.getOrgId(), user.orgId()) || recording.getStatus() != RecordingStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_PLAYABLE", "Recording is not playable");
        }
        OffsetDateTime expiresAt = now().plusSeconds(properties.getRecording().getPlayUrlTtlSeconds());
        String token = signPlayback(recordingId, expiresAt.toEpochSecond());
        String path = "/api/control/recordings/" + recordingId + "/hls/index.m3u8?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return new PlaybackUrlResponse(recordingId, "hls", "application/vnd.apple.mpegurl", path, expiresAt);
    }

    public PlaybackAsset playbackAsset(String recordingId, String objectName, String token) {
        verifyPlayback(recordingId, token);
        if (!objectName.matches("[A-Za-z0-9_.-]+")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ASSET_NAME", "Invalid HLS asset name");
        }
        MediaRecording recording = requireRecording(recordingId);
        if (recording.getStatus() != RecordingStatus.READY) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_PLAYABLE", "Recording is not playable");
        }
        String objectKey = hlsPrefix(recording) + objectName;
        byte[] bytes = storage.readObject(objectKey);
        if (objectName.endsWith(".m3u8")) {
            String playlist = new String(bytes, StandardCharsets.UTF_8);
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String rewritten = playlist.lines()
                    .map(line -> line.isBlank() || line.startsWith("#") || line.contains("://")
                            ? line
                            : line + (line.contains("?") ? "&" : "?") + "token=" + encodedToken)
                    .reduce("", (left, line) -> left + line + "\n");
            return new PlaybackAsset(rewritten.getBytes(StandardCharsets.UTF_8), "application/vnd.apple.mpegurl");
        }
        return new PlaybackAsset(bytes, objectName.endsWith(".ts") ? "video/mp2t" : "application/octet-stream");
    }

    @Transactional
    public void expireUpload(MediaRecordingUpload upload) {
        if (upload.getStatus() != UploadStatus.ACTIVE) {
            return;
        }
        MediaRecording recording = requireRecording(upload.getRecordingId());
        storage.abortMultipart(recording.getSourceObjectKey(), upload.getStorageUploadId());
        upload.setStatus(UploadStatus.EXPIRED);
        recording.setStatus(RecordingStatus.FAILED);
        recording.setErrorCode("UPLOAD_EXPIRED");
        recording.setErrorMessage("Upload session expired");
        recording.setUpdatedAt(now());
        uploadRepository.save(upload);
        recordingRepository.save(recording);
    }

    @Transactional
    public void deleteRecordingAssets(MediaRecording recording) {
        if (recording.getStatus() != RecordingStatus.READY && recording.getStatus() != RecordingStatus.DELETING) {
            return;
        }
        recording.setStatus(RecordingStatus.DELETING);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
        String prefix = recording.getSourceObjectKey().substring(
                0,
                recording.getSourceObjectKey().indexOf("/original/source.mp4") + 1);
        storage.deletePrefix(prefix);
        recording.setStatus(RecordingStatus.DELETED);
        recording.setUpdatedAt(now());
        recordingRepository.save(recording);
    }

    private MediaRecording newRecording(String robotId, CreateRecordingUploadRequest request) {
        OffsetDateTime timestamp = now();
        OffsetDateTime recordedAt = request.getRecordedStartedAt() == null ? timestamp : request.getRecordedStartedAt();
        MediaRecording recording = new MediaRecording();
        recording.setRecordingId("rec_" + id());
        recording.setOrgId(properties.getRecording().getDefaultOrgId());
        recording.setRobotId(robotId);
        recording.setDeviceId(request.getDeviceId());
        recording.setSourceFileId(request.getSourceFileId());
        recording.setFileName(request.getFileName());
        recording.setSourceContentType(request.getContentType());
        recording.setFileSize(request.getFileSize());
        recording.setSha256(request.getSha256());
        recording.setRecordedStartedAt(recordedAt);
        recording.setReportedDurationSeconds(request.getDurationSeconds());
        recording.setSourceObjectKey(objectKey(recording, recordedAt));
        recording.setStatus(RecordingStatus.CREATED);
        recording.setCreatedAt(timestamp);
        recording.setUpdatedAt(timestamp);
        return recording;
    }

    private MediaRecordingUpload newUpload(MediaRecording recording) {
        OffsetDateTime timestamp = now();
        MediaRecordingUpload upload = new MediaRecordingUpload();
        upload.setUploadId("upl_" + id());
        upload.setRecordingId(recording.getRecordingId());
        upload.setStorageUploadId(storage.initiateMultipart());
        upload.setPartSize(properties.getRecording().getPartSizeBytes());
        upload.setPartCount((int) Math.ceil((double) recording.getFileSize() / upload.getPartSize()));
        upload.setStatus(UploadStatus.ACTIVE);
        upload.setCreatedAt(timestamp);
        upload.setLastActiveAt(timestamp);
        upload.setExpiresAt(timestamp.plusHours(properties.getRecording().getUploadExpireHours()));
        return upload;
    }

    private RecordingUploadResponse uploadResponse(MediaRecording recording, MediaRecordingUpload upload, boolean uploadRequired) {
        List<PartInfoResponse> parts = storage.listParts(recording.getSourceObjectKey(), upload.getStorageUploadId()).stream()
                .map(part -> new PartInfoResponse(part.partNumber(), part.etag(), part.size()))
                .toList();
        return new RecordingUploadResponse(
                recording.getRecordingId(),
                upload.getUploadId(),
                recording.getStatus().name(),
                uploadRequired,
                upload.getPartSize(),
                upload.getPartCount(),
                parts,
                upload.getExpiresAt());
    }

    private RecordingUploadResponse completedResponse(MediaRecording recording) {
        return new RecordingUploadResponse(
                recording.getRecordingId(),
                null,
                recording.getStatus().name(),
                false,
                properties.getRecording().getPartSizeBytes(),
                0,
                List.of(),
                null);
    }

    private void validateUploadedParts(MediaRecording recording, MediaRecordingUpload upload, List<StoredPart> parts) {
        if (parts.size() != upload.getPartCount()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "Not all parts have been uploaded");
        }
        long total = 0;
        for (int index = 0; index < parts.size(); index++) {
            StoredPart part = parts.get(index);
            if (part.partNumber() != index + 1) {
                throw error(HttpStatus.CONFLICT, "UPLOAD_INCOMPLETE", "Uploaded parts are not contiguous");
            }
            total += part.size();
        }
        if (total != recording.getFileSize()) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_SIZE_MISMATCH", "Uploaded part sizes do not match registered file");
        }
    }

    private void validateSource(CreateRecordingUploadRequest request) {
        if (!"video/mp4".equalsIgnoreCase(request.getContentType())) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_VIDEO_FILE", "Only video/mp4 is accepted");
        }
        if (request.getFileSize() > properties.getRecording().getMaxFileSizeBytes()) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Recording exceeds configured size limit");
        }
    }

    private void ensureSameSource(MediaRecording recording, CreateRecordingUploadRequest request) {
        if (recording.getFileSize() != request.getFileSize()
                || !Objects.equals(recording.getSha256(), request.getSha256())) {
            throw error(HttpStatus.CONFLICT, "SOURCE_FILE_CHANGED", "Source file changed since upload was created");
        }
    }

    private void checkQuota(String robotId) {
        if (uploadRepository.countByStatus(UploadStatus.ACTIVE) >= properties.getRecording().getMaxActiveUploadsGlobal()
                || uploadRepository.countActiveByRobotId(robotId, UploadStatus.ACTIVE)
                        >= properties.getRecording().getMaxActiveUploadsPerRobot()) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "UPLOAD_CONCURRENCY_LIMIT", "Upload concurrency limit exceeded");
        }
    }

    private MediaRecordingUpload requireActiveUpload(String robotId, String uploadId) {
        MediaRecordingUpload upload = requireUpload(robotId, uploadId);
        if (upload.getStatus() != UploadStatus.ACTIVE || upload.getExpiresAt().isBefore(now())) {
            throw error(HttpStatus.CONFLICT, "UPLOAD_NOT_ACTIVE", "Upload session is no longer active");
        }
        return upload;
    }

    private MediaRecordingUpload requireUpload(String robotId, String uploadId) {
        MediaRecordingUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "Upload not found"));
        requireRobotRecording(robotId, upload.getRecordingId());
        return upload;
    }

    private MediaRecording requireRobotRecording(String robotId, String recordingId) {
        MediaRecording recording = requireRecording(recordingId);
        if (!recording.getRobotId().equals(robotId)) {
            throw error(HttpStatus.NOT_FOUND, "RECORDING_NOT_FOUND", "Recording not found");
        }
        return recording;
    }

    private MediaRecording requireRecording(String recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "RECORDING_NOT_FOUND", "Recording not found"));
    }

    private void refresh(MediaRecordingUpload upload) {
        if (properties.getRecording().isUploadSessionRefreshEnabled()) {
            upload.setExpiresAt(now().plusHours(properties.getRecording().getUploadExpireHours()));
        }
        upload.setLastActiveAt(now());
        uploadRepository.save(upload);
    }

    private RecordingStatusResponse status(MediaRecording recording) {
        return new RecordingStatusResponse(
                recording.getRecordingId(),
                recording.getStatus().name(),
                recording.getFileSize(),
                recording.getDurationSeconds(),
                recording.getStatus() == RecordingStatus.READY,
                recording.getErrorCode(),
                recording.getErrorMessage(),
                recording.getUploadedAt());
    }

    private RecordingListItemResponse item(MediaRecording recording) {
        return new RecordingListItemResponse(
                recording.getRecordingId(),
                recording.getRobotId(),
                recording.getDeviceId(),
                recording.getFileName(),
                recording.getFileSize(),
                recording.getDurationSeconds(),
                recording.getRecordedStartedAt(),
                recording.getStatus().name(),
                recording.getErrorCode(),
                recording.getUploadedAt());
    }

    private String objectKey(MediaRecording recording, OffsetDateTime time) {
        return "recordings/%s/%s/%s/%04d/%02d/%02d/%s/original/source.mp4".formatted(
                recording.getOrgId(),
                recording.getRobotId(),
                recording.getDeviceId(),
                time.getYear(),
                time.getMonthValue(),
                time.getDayOfMonth(),
                recording.getRecordingId());
    }

    public String hlsPrefix(MediaRecording recording) {
        return recording.getSourceObjectKey().replace("/original/source.mp4", "/hls/");
    }

    private String signPlayback(String recordingId, long expiresAt) {
        String payload = recordingId + "." + expiresAt;
        return payload + "." + signature(payload);
    }

    private void verifyPlayback(String recordingId, String token) {
        if (token == null) {
            throw error(HttpStatus.FORBIDDEN, "PLAY_TOKEN_REQUIRED", "Playback token is required");
        }
        String[] values = token.split("\\.");
        if (values.length != 3 || !recordingId.equals(values[0])) {
            throw error(HttpStatus.FORBIDDEN, "INVALID_PLAY_TOKEN", "Playback token is invalid");
        }
        long expiry;
        try {
            expiry = Long.parseLong(values[1]);
        } catch (NumberFormatException ex) {
            throw error(HttpStatus.FORBIDDEN, "INVALID_PLAY_TOKEN", "Playback token is invalid");
        }
        String payload = values[0] + "." + values[1];
        if (expiry < now().toEpochSecond() || !constantEquals(values[2], signature(payload))) {
            throw error(HttpStatus.FORBIDDEN, "PLAY_TOKEN_EXPIRED", "Playback token expired or invalid");
        }
    }

    private String signature(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    properties.getRecording().getPlayTokenSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create playback token", ex);
        }
    }

    private boolean constantEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private void requireRobotId(String robotId) {
        if (robotId == null || robotId.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "ROBOT_ID_REQUIRED", "X-Robot-Id is required");
        }
    }

    private RecordingApiException error(HttpStatus status, String code, String message) {
        return new RecordingApiException(status, code, message);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record PlaybackAsset(byte[] bytes, String contentType) {
    }
}
