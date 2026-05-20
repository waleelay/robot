package com.robot.mediaserver.video.dto;

/**
 * 抓拍任务失败请求。
 *
 * @author leelay
 * @date 2026/05/20
 */
public class FailSnapshotRequest {

    private String errorCode = "SNAPSHOT_FAILED";
    private String errorMessage;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
