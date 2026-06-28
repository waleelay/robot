package com.robot.bigscreen.client;

import com.robot.bigscreen.config.CenterServiceProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CenterProxyClient {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length");

    private final RestClient restClient;
    private final CenterServiceProperties properties;

    public CenterProxyClient(RestClient.Builder builder, CenterServiceProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public ResponseEntity<byte[]> forward(HttpServletRequest request, byte[] body) {
        if (isMultipart(request)) {
            return forwardMultipart(request);
        }
        String target = targetBaseUrl(request) + targetPath(request);
        String query = request.getQueryString();
        URI uri = UriComponentsBuilder.fromUriString(target)
                .query(query)
                .build(true)
                .toUri();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        ResponseEntity<byte[]> response = restClient.method(method)
                .uri(uri)
                .headers(headers -> copyRequestHeaders(request, headers))
                .body(body == null ? new byte[0] : body)
                .retrieve()
                .toEntity(byte[].class);
        return ResponseEntity.status(response.getStatusCode())
                .headers(sanitizeResponseHeaders(response.getHeaders()))
                .body(response.getBody());
    }

    private ResponseEntity<byte[]> forwardMultipart(HttpServletRequest request) {
        String target = targetBaseUrl(request) + targetPath(request);
        String query = request.getQueryString();
        URI uri = UriComponentsBuilder.fromUriString(target)
                .query(query)
                .build(true)
                .toUri();
        ResponseEntity<byte[]> response = restClient.method(HttpMethod.valueOf(request.getMethod()))
                .uri(uri)
                .headers(headers -> copyRequestHeaders(request, headers, false))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody(request))
                .retrieve()
                .toEntity(byte[].class);
        return ResponseEntity.status(response.getStatusCode())
                .headers(sanitizeResponseHeaders(response.getHeaders()))
                .body(response.getBody());
    }

    private String targetBaseUrl(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/manage")) {
            return properties.getManageBaseUrl();
        }
        if (path.startsWith("/api/media") || path.startsWith("/internal/media")) {
            return properties.getMediaBaseUrl();
        }
        return properties.getControlBaseUrl();
    }

    private String targetPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/bigscreen")) {
            return "/api/control" + path.substring("/api/bigscreen".length());
        }
        return path;
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        copyRequestHeaders(request, headers, true);
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers, boolean includeContentType) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return;
        }
        for (String name : Collections.list(names)) {
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            if (!includeContentType && HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                continue;
            }
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MultiValueMap<String, Object> multipartBody(HttpServletRequest request) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            for (Part part : request.getParts()) {
                String filename = part.getSubmittedFileName();
                byte[] bytes = part.getInputStream().readAllBytes();
                if (filename == null) {
                    body.add(part.getName(), new String(bytes, request.getCharacterEncoding() == null ? "UTF-8" : request.getCharacterEncoding()));
                } else {
                    body.add(part.getName(), new ByteArrayResource(bytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    });
                }
            }
        } catch (IOException | ServletException ex) {
            throw new IllegalStateException("转发 multipart 请求失败", ex);
        }
        return body;
    }

    private HttpHeaders sanitizeResponseHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, values);
            }
        });
        return headers;
    }
}
