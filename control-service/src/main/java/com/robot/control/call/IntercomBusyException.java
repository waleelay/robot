package com.robot.control.call;

/** Business conflict raised when a robot, operator, or browser is already in intercom. */
public class IntercomBusyException extends IllegalStateException {

    private final String code;

    public IntercomBusyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
