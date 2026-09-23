package com.robot.mediaserver.file.service;

import com.robot.mediaserver.file.repository.MediaFileSourceLockRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class FileSourceLockService {

    private final MediaFileSourceLockRepository repository;

    public FileSourceLockService(MediaFileSourceLockRepository repository) {
        this.repository = repository;
    }

    /** 必须在调用方事务内执行，锁持有到文件记录创建或恢复完成。 */
    public void lock(String robotId, String sourceFileId) {
        String lockId = digest(robotId + "\0" + sourceFileId);
        repository.insertIgnore(lockId, robotId, sourceFileId, OffsetDateTime.now(ZoneOffset.UTC));
        repository.findByIdForUpdate(lockId)
                .orElseThrow(() -> new IllegalStateException("无法获取来源文件创建锁"));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成来源文件锁标识", exception);
        }
    }
}
