package com.robot.control.call;

/** Robot initiated intercom call state. */
public enum IntercomCallStatus {
    RINGING,
    ACCEPTED,
    REJECTED,
    TIMEOUT,
    CANCELED,
    BUSY,
    ENDED,
    FAILED
}
