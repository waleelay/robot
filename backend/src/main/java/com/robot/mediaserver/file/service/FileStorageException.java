package com.robot.mediaserver.file.service;

/**
 * 文件对象存储访问异常。对外统一映射为可重试的 503，内部保留原始异常便于排查。
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
