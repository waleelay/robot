package com.robot.mediaserver.rtsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.rtsp.dto.RtspProbeResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class RtspProbeService {

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;

    public RtspProbeService(MediaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RtspProbeResponse probe(String url) {
        String targetUrl = url == null || url.isBlank() ? properties.getRtsp().getDefaultUrl() : url;
        try {
            Process process = new ProcessBuilder(
                    properties.getRtsp().getFfprobePath(),
                    "-v", "error",
                    "-rtsp_transport", "tcp",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name,width,height",
                    "-of", "json",
                    targetUrl)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            boolean done = process.waitFor(properties.getRtsp().getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return new RtspProbeResponse(false, targetUrl, null, null, null, "timeout");
            }
            String body = output.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new RtspProbeResponse(false, targetUrl, null, null, null, body);
            }
            JsonNode stream = objectMapper.readTree(body).path("streams").path(0);
            return new RtspProbeResponse(true, targetUrl, text(stream, "codec_name"), integer(stream, "width"), integer(stream, "height"), "ok");
        } catch (Exception ex) {
            return new RtspProbeResponse(false, targetUrl, null, null, null, ex.getMessage());
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }
}
