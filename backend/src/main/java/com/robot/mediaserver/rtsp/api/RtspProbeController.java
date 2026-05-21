package com.robot.mediaserver.rtsp.api;

import com.robot.mediaserver.rtsp.RtspProbeService;
import com.robot.mediaserver.rtsp.dto.RtspProbeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RtspProbeController {

    private final RtspProbeService service;

    public RtspProbeController(RtspProbeService service) {
        this.service = service;
    }

    @GetMapping("/api/media/rtsp/probe")
    public RtspProbeResponse probe(@RequestParam(required = false) String url) {
        return service.probe(url);
    }
}
