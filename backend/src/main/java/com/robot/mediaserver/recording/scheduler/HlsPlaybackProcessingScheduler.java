package com.robot.mediaserver.recording.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.recording.service.HlsPlaybackAssetService;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HlsPlaybackProcessingScheduler {

    private final MediaProperties properties;
    private final HlsPlaybackAssetService service;
    private final ExecutorService executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public HlsPlaybackProcessingScheduler(MediaProperties properties, HlsPlaybackAssetService service) {
        this.properties = properties;
        this.service = service;
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getRecording().getHlsWorkerConcurrency()));
    }

    @Scheduled(fixedDelayString = "${media.recording.hls-poll-delay-ms:3000}")
    public void processPending() {
        if (!properties.getRecording().isEnabled()) {
            return;
        }
        while (inFlight.size() < Math.max(1, properties.getRecording().getHlsWorkerConcurrency())) {
            var candidate = service.claimNext();
            if (candidate.isEmpty() || !inFlight.add(candidate.get())) {
                return;
            }
            String recordingId = candidate.get();
            executor.execute(() -> {
                try {
                    service.process(recordingId);
                } finally {
                    inFlight.remove(recordingId);
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
