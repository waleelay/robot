package com.robot.control.call;

/** 机器人、操作员或浏览器已被对讲占用时抛出的业务冲突异常。 */
public class IntercomBusyException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private final String code;

    /**
     * 初始化 IntercomBusyException，保存所需依赖及初始运行状态。
     *
     * @param code 业务错误码
     * @param message 消息内容
     */
    public IntercomBusyException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 读取对讲占用冲突对应的稳定业务错误码。
     *
     * @return 调用方可识别的冲突码
     */
    public String code() {
        return code;
    }
}
