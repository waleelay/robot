package com.robot.mediaserver.recording.model;

public enum RecordingStatus {
    CREATED,
    UPLOADING,
    VERIFYING,
    PROCESSING_PLAYBACK,
    READY,
    FAILED,
    DELETING,
    DELETED
}
