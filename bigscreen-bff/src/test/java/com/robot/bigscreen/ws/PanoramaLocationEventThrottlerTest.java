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
    void publishesGisFirstAndKeepsLaterSlamForTheNextWindow() {
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
        String gis = gisLocation("robot-1", "map-1", 2, 104.1, 30.2);
        throttler.publish("browser-a", gis, published::add);
        String laterSlam = fallbackLocation("robot-1", "map-1", 3);
        throttler.publish("browser-a", laterSlam, published::add);
        nanoTime.set(1_000_000_000L);
        scheduled.get().run();

        assertThat(published).containsExactly(location("robot-1", 1), gis);
        nanoTime.set(2_000_000_000L);
        scheduled.get().run();
        assertThat(published).containsExactly(location("robot-1", 1), gis, laterSlam);
    }

    @Test
    void publishesSlamInNextWindowWhenNoNewGisArrives() {
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

        String gis = gisLocation("robot-1", "map-1", 1, 104.1, 30.2);
        throttler.publish("browser-a", gis, published::add);
        nanoTime.set(100_000_000L);
        String slam = fallbackLocation("robot-1", "map-1", 2);
        throttler.publish("browser-a", slam, published::add);
        nanoTime.set(1_000_000_000L);
        scheduled.get().run();

        assertThat(published).containsExactly(gis, slam);
    }

    @Test
    void acceptsSlamOnlyLocationAfterMapChanges() {
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

        throttler.publish("browser-a", gisLocation("robot-1", "map-1", 1, 104.1, 30.2), published::add);
        String nextMap = mapLocation("robot-1", "map-2", 2);
        throttler.publish("browser-a", nextMap, published::add);
        nanoTime.set(1_000_000_000L);
        scheduled.get().run();

        assertThat(published).containsExactly(
                gisLocation("robot-1", "map-1", 1, 104.1, 30.2),
                nextMap);
        verify(scheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
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

    private String mapLocation(String robotId, String mapId, int x) {
        return "{\"event\":\"panorama.device.location.changed\",\"data\":{\"robotId\":\""
                + robotId + "\",\"location\":{\"mapId\":\"" + mapId + "\",\"x\":" + x + "}}}";
    }

    private String fallbackLocation(String robotId, String mapId, int x) {
        return "{\"event\":\"panorama.device.location.changed\",\"data\":{\"robotId\":\""
                + robotId + "\",\"location\":{\"mapId\":\"" + mapId + "\",\"x\":" + x
                + ",\"y\":2}}}";
    }

    private String gisLocation(String robotId, String mapId, int x, double lng, double lat) {
        return "{\"event\":\"panorama.device.location.changed\",\"data\":{\"robotId\":\""
                + robotId + "\",\"location\":{\"mapId\":\"" + mapId + "\",\"x\":" + x
                + ",\"lng\":" + lng + ",\"lat\":" + lat + "}}}";
    }
}
