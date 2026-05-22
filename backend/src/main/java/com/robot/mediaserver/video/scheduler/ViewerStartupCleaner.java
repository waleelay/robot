package com.robot.mediaserver.video.scheduler;

import com.robot.mediaserver.video.service.VideoSessionService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ViewerStartupCleaner {

    private final VideoSessionService videoSessionService;

    public ViewerStartupCleaner(VideoSessionService videoSessionService) {
        this.videoSessionService = videoSessionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeActiveViewers() {
        videoSessionService.closeAllActiveViewers();
    }
}
