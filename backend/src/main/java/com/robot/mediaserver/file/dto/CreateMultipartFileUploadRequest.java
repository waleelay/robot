package com.robot.mediaserver.file.dto;

import com.robot.mediaserver.file.model.FileType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateMultipartFileUploadRequest {
    private String robotId;
    private String deviceId;
    private String taskExecutionId;
    private String sourceFileId;

    @NotNull
    private FileType fileType;

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @Min(1)
    private long fileSize;

    private String metadata;

    public String getRobotId() { return robotId; }
    public void setRobotId(String robotId) { this.robotId = robotId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTaskExecutionId() { return taskExecutionId; }
    public void setTaskExecutionId(String taskExecutionId) { this.taskExecutionId = taskExecutionId; }
    public String getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(String sourceFileId) { this.sourceFileId = sourceFileId; }
    public FileType getFileType() { return fileType; }
    public void setFileType(FileType fileType) { this.fileType = fileType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
