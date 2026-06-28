package com.robot.mediaserver.tts.api;

import com.robot.mediaserver.tts.service.TtsAudioService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media/tts")
public class RobotTtsController {

    private final TtsAudioService service;

    public RobotTtsController(TtsAudioService service) {
        this.service = service;
    }

    @GetMapping("/generate-file")
    public ResponseEntity<FileSystemResource> generateFile(
            @RequestHeader("X-Robot-Id") String robotId,
            @RequestParam String text) {
        return service.generateAndReturnFile(robotId, text);
    }

    @GetMapping("/generate-and-play")
    public void generateAndPublishToFrontend(
            @RequestHeader("X-Robot-Id") String robotId,
            @RequestParam String text) {
        service.generateAndPublishToFrontend(robotId, text);
    }
}
