package com.robot.bigscreen.client;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CenterProxyClient {

    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final int MAX_RESPONSE_BODY_BYTES = 32 * 1024 * 1024;

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
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;

    public CenterProxyClient(
            RestClient.Builder builder,
            CenterServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
    }

    public ResponseEntity<byte[]> forward(HttpServletRequest request) {
        return forward(request, targetBaseUrl(request), targetPath(request));
    }

    public ResponseEntity<byte[]> forwardToManage(HttpServletRequest request, String targetPath) {
        return forward(request, properties.getManageBaseUrl(), targetPath);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, String targetBaseUrl, String targetPath) {
        if (isMultipart(request)) {
            return forwardMultipart(request, targetBaseUrl, targetPath);
        }
        String target = targetBaseUrl + targetPath;
        String query = stripAccessToken(request.getQueryString());
        URI uri = UriComponentsBuilder.fromUriString(target)
                .query(query)
                .build(true)
                .toUri();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return restClient.method(method)
                .uri(uri)
                .headers(headers -> copyRequestHeaders(request, headers))
                .body(requestBody(request))
                .exchange((clientRequest, clientResponse) -> ResponseEntity.status(clientResponse.getStatusCode())
                        .headers(sanitizeResponseHeaders(clientResponse.getHeaders()))
                        .body(readBounded(clientResponse.getBody(), MAX_RESPONSE_BODY_BYTES, "下游响应超过允许大小")));
    }

    private ResponseEntity<byte[]> forwardMultipart(HttpServletRequest request, String targetBaseUrl, String targetPath) {
        String target = targetBaseUrl + targetPath;
        String query = stripAccessToken(request.getQueryString());
        URI uri = UriComponentsBuilder.fromUriString(target)
                .query(query)
                .build(true)
                .toUri();
        return restClient.method(HttpMethod.valueOf(request.getMethod()))
                .uri(uri)
                .headers(headers -> copyRequestHeaders(request, headers, false))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody(request))
                .exchange((clientRequest, clientResponse) -> ResponseEntity.status(clientResponse.getStatusCode())
                        .headers(sanitizeResponseHeaders(clientResponse.getHeaders()))
                        .body(readBounded(clientResponse.getBody(), MAX_RESPONSE_BODY_BYTES, "下游响应超过允许大小")));
    }

    private String targetBaseUrl(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/manage") || path.startsWith("/api/v1/management")) {
            return properties.getManageBaseUrl();
        }
        if (path.startsWith("/api/v1/control")) {
            return properties.getV1ControlBaseUrl();
        }
        if (path.startsWith("/api/media")) {
            return properties.getMediaBaseUrl();
        }
        return properties.getControlBaseUrl();
    }

    private String targetPath(HttpServletRequest request) {
        return targetPath(request.getRequestURI());
    }

    static String targetPath(String path) {
        if (path.startsWith("/api/bigscreen/control")) {
            return "/api/control" + path.substring("/api/bigscreen/control".length());
        }
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
        if (names != null) {
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
        authenticatedRequestHeaders.apply(headers);
    }

    static String stripAccessToken(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        String[] pairs = query.split("&");
        StringBuilder kept = new StringBuilder();
        for (String pair : pairs) {
            if (pair.startsWith("access_token=")) {
                continue;
            }
            if (!kept.isEmpty()) {
                kept.append('&');
            }
            kept.append(pair);
        }
        return kept.toString();
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
                if (filename == null) {
                    byte[] bytes = readBounded(part.getInputStream(), MAX_REQUEST_BODY_BYTES, "multipart 文本字段超过允许大小");
                    body.add(part.getName(), new String(bytes, request.getCharacterEncoding() == null ? "UTF-8" : request.getCharacterEncoding()));
                } else {
                    body.add(part.getName(), filePartResource(part));
                }
            }
        } catch (IOException | ServletException ex) {
            throw new IllegalStateException("转发 multipart 请求失败", ex);
        }
        return body;
    }

    private byte[] requestBody(HttpServletRequest request) {
        try {
            return readBounded(request.getInputStream(), MAX_REQUEST_BODY_BYTES, "请求体超过允许大小");
        } catch (IOException ex) {
            throw new IllegalStateException("读取转发请求体失败", ex);
        }
    }

    static InputStreamResource filePartResource(Part part) throws IOException {
        String filename = part.getSubmittedFileName();
        return new InputStreamResource(part.getInputStream()) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return part.getSize();
            }
        };
    }

    static byte[] readBounded(InputStream input, int maxBytes, String message) throws IOException {
        byte[] bytes = input.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, message);
        }
        return bytes;
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
