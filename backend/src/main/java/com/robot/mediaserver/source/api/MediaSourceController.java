package com.robot.mediaserver.source.api;

import com.robot.mediaserver.source.dto.MediaSourceRequest;
import com.robot.mediaserver.source.dto.MediaSourceResponse;
import com.robot.mediaserver.source.service.MediaSourceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media/sources")
public class MediaSourceController {

    private final MediaSourceService service;

    public MediaSourceController(MediaSourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<MediaSourceResponse> list() {
        return service.list();
    }

    @PostMapping
    public MediaSourceResponse save(@Valid @RequestBody MediaSourceRequest request) {
        return service.save(request);
    }

    @PutMapping("/{sourceId}")
    public MediaSourceResponse update(@PathVariable String sourceId, @Valid @RequestBody MediaSourceRequest request) {
        return service.update(sourceId, request);
    }

    @DeleteMapping("/{sourceId}")
    public void delete(@PathVariable String sourceId) {
        service.delete(sourceId);
    }
}
