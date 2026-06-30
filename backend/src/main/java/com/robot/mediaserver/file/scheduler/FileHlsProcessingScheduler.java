package com.robot.mediaserver.file.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileHlsProcessingService;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileHlsProcessingScheduler {

    private final MediaProperties properties;
    private final FileHlsProcessingService service;
    private final ExecutorService executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public FileHlsProcessingScheduler(MediaProperties properties, FileHlsProcessingService service) {
        this.properties = properties;
        this.service = service;
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getFile().getHlsWorkerConcurrency()));
    }

    @Scheduled(fixedDelayString = "${media.file.hls-poll-delay-ms:3000}")
    public void poll() {
        if (!properties.getFile().isEnabled()) {
            return;
        }
        while (inFlight.size() < Math.max(1, properties.getFile().getHlsWorkerConcurrency())) {
            var candidate = service.claimNext();
            if (candidate.isEmpty() || !inFlight.add(candidate.get())) {
                return;
            }
            String fileId = candidate.get();
            executor.submit(() -> {
                try {
                    service.process(fileId);
                } finally {
                    inFlight.remove(fileId);
                }
            });
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
