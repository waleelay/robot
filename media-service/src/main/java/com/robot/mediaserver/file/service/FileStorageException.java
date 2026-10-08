package com.robot.mediaserver.file.service;

/**
 * 文件对象存储访问异常。对外统一映射为可重试的 503，内部保留原始异常便于排查。
 */
public class FileStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 初始化 FileStorageException，保存所需依赖及初始运行状态。
     *
     * @param message 消息内容
     */
    public FileStorageException(String message) {
        super(message);
    }

    /**
     * 初始化 FileStorageException，保存所需依赖及初始运行状态。
     *
     * @param message 消息内容
     * @param cause 触发当前异常的原始原因
     */
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
