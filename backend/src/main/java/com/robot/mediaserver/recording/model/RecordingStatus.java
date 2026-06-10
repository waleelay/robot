package com.robot.mediaserver.recording.model;

public enum RecordingStatus {
    CREATED,
    RECORDING,
    UPLOADING,
    VERIFYING,
    PROCESSING_PLAYBACK,
    READY,
    FAILED,
    DELETING,
    DELETED
}
