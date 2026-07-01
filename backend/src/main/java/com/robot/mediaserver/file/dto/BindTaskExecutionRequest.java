package com.robot.mediaserver.file.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class BindTaskExecutionRequest {

    @NotBlank
    private String taskExecutionId;

    private List<String> videoFileIds = List.of();
    private String pointFileId;

    public String getTaskExecutionId() {
        return taskExecutionId;
    }

    public void setTaskExecutionId(String taskExecutionId) {
        this.taskExecutionId = taskExecutionId;
    }

    public List<String> getVideoFileIds() {
        return videoFileIds;
    }

    public void setVideoFileIds(List<String> videoFileIds) {
        this.videoFileIds = videoFileIds == null ? List.of() : videoFileIds;
    }

    public String getPointFileId() {
        return pointFileId;
    }

    public void setPointFileId(String pointFileId) {
        this.pointFileId = pointFileId;
    }
}
