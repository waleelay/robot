package com.robot.bigscreen.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class PanoramaLocationEventThrottlerTest {

    @Test
    void publishesFirstLocationImmediatelyAndLatestLocationAfterOneSecond() {
        AtomicLong nanoTime = new AtomicLong();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        TaskScheduler scheduler = mock(TaskScheduler.class);
        doAnswer(invocation -> {
            scheduled.set(invocation.getArgument(0));
            return null;
        }).when(scheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
        PanoramaLocationEventThrottler throttler = new PanoramaLocationEventThrottler(
                new ObjectMapper(), scheduler, nanoTime::get);
        List<String> published = new ArrayList<>();

        throttler.publish("browser-a", location("robot-1", 1), published::add);
        nanoTime.set(100_000_000L);
        throttler.publish("browser-a", location("robot-1", 2), published::add);
        nanoTime.set(900_000_000L);
        throttler.publish("browser-a", location("robot-1", 3), published::add);

        assertThat(published).containsExactly(location("robot-1", 1));
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(java.time.Instant.class));

        nanoTime.set(1_000_000_000L);
        scheduled.get().run();

        assertThat(published).containsExactly(location("robot-1", 1), location("robot-1", 3));
    }

    @Test
    void isolatesRateLimitByBrowserAndRobot() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        PanoramaLocationEventThrottler throttler = new PanoramaLocationEventThrottler(
                new ObjectMapper(), scheduler, () -> 0L);
        List<String> published = new ArrayList<>();

        throttler.publish("browser-a", location("robot-1", 1), published::add);
        throttler.publish("browser-a", location("robot-2", 2), published::add);
        throttler.publish("browser-b", location("robot-1", 3), published::add);

        assertThat(published).containsExactly(
                location("robot-1", 1),
                location("robot-2", 2),
                location("robot-1", 3));
    }

    @Test
    void publishesLocalizationInvalidationImmediately() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        PanoramaLocationEventThrottler throttler = new PanoramaLocationEventThrottler(
                new ObjectMapper(), scheduler, () -> 0L);
        List<String> published = new ArrayList<>();

        throttler.publish("browser-a", location("robot-1", 1), published::add);
        throttler.publish("browser-a", """
                {"event":"panorama.device.location.changed","data":{"robotId":"robot-1","location":{"localized":false}}}
                """, published::add);

        assertThat(published).hasSize(2);
    }

    @Test
    void forwardsOtherEventsWithoutRateLimit() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        PanoramaLocationEventThrottler throttler = new PanoramaLocationEventThrottler(
                new ObjectMapper(), scheduler, () -> 0L);
        List<String> published = new ArrayList<>();
        String status = "{\"event\":\"panorama.device.status.changed\",\"data\":{}}";

        throttler.publish("browser-a", status, published::add);
        throttler.publish("browser-a", status, published::add);

        assertThat(published).containsExactly(status, status);
    }

    private String location(String robotId, int x) {
        return "{\"event\":\"panorama.device.location.changed\",\"data\":{\"robotId\":\""
                + robotId + "\",\"location\":{\"x\":" + x + "}}}";
    }
}
