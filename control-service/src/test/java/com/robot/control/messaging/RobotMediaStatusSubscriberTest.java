package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.config.ControlServiceProperties;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.EquipmentControlService;
import org.junit.jupiter.api.Test;

class RobotMediaStatusSubscriberTest {

    @Test
    void tracksMediaClientOnlineTransitionIndependentlyFromRobotOnlineStatus() {
        RobotMediaStatusSubscriber subscriber = new RobotMediaStatusSubscriber(
                new ControlServiceProperties(),
                new ObjectMapper(),
                mock(ControlMediaServiceClient.class),
                mock(RobotMediaCommandService.class),
                mock(EquipmentControlService.class),
                mock(RobotRegistryService.class),
                mock(IntercomCallService.class),
                mock(EdgeDeviceStatusHandler.class));

        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isTrue();
        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isFalse();
        assertThat(subscriber.mediaClientBecameOnline("study", "offline")).isFalse();
        assertThat(subscriber.mediaClientBecameOnline("study", "online")).isTrue();
    }
}
