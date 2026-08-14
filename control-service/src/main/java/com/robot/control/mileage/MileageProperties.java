package com.robot.control.mileage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 设备里程增量计算参数。 */
@ConfigurationProperties(prefix = "control.mileage")
public class MileageProperties {

    private double maxSpeedMps = 15.0;
    private double publishDistanceThresholdMeters = 10.0;

    public double getMaxSpeedMps() {
        return maxSpeedMps;
    }

    public void setMaxSpeedMps(double maxSpeedMps) {
        this.maxSpeedMps = maxSpeedMps;
    }

    public double getPublishDistanceThresholdMeters() {
        return publishDistanceThresholdMeters;
    }

    public void setPublishDistanceThresholdMeters(double publishDistanceThresholdMeters) {
        this.publishDistanceThresholdMeters = publishDistanceThresholdMeters;
    }
}
