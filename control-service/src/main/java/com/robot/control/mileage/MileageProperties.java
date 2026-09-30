package com.robot.control.mileage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 设备里程增量计算参数。 */
@ConfigurationProperties(prefix = "control.mileage")
public class MileageProperties {

    /**
     * 里程读数可信度判断使用的速度上限，单位米每秒。
     */
    private double maxSpeedMps = 15.0;
    /**
     * 触发里程变更事件的未发布有效里程累计阈值，单位米
     */
    private double publishDistanceThresholdMeters = 10.0;

    /**
     * 读取{@link #maxSpeedMps}。
     *
     * @return 当前值，含义与约束见{@link #maxSpeedMps}
     */
    public double getMaxSpeedMps() {
        return maxSpeedMps;
    }

    /**
     * 更新{@link #maxSpeedMps}。
     *
     * @param maxSpeedMps 新值，含义与约束见{@link #maxSpeedMps}
     */
    public void setMaxSpeedMps(double maxSpeedMps) {
        this.maxSpeedMps = maxSpeedMps;
    }

    /**
     * 读取{@link #publishDistanceThresholdMeters}。
     *
     * @return 当前值，含义与约束见{@link #publishDistanceThresholdMeters}
     */
    public double getPublishDistanceThresholdMeters() {
        return publishDistanceThresholdMeters;
    }

    /**
     * 更新{@link #publishDistanceThresholdMeters}。
     *
     * @param publishDistanceThresholdMeters 新值，含义与约束见{@link #publishDistanceThresholdMeters}
     */
    public void setPublishDistanceThresholdMeters(double publishDistanceThresholdMeters) {
        this.publishDistanceThresholdMeters = publishDistanceThresholdMeters;
    }
}
