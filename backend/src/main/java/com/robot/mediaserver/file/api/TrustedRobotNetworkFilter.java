package com.robot.mediaserver.file.api;

import com.robot.mediaserver.config.MediaProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TrustedRobotNetworkFilter extends OncePerRequestFilter {

    private final MediaProperties properties;

    public TrustedRobotNetworkFilter(MediaProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean robotUploadPath = path.startsWith("/api/media/files/multipart-uploads")
                || (path.startsWith("/api/media/files/") && path.endsWith("/status"));
        return !robotUploadPath || !properties.getFile().isTrustedRobotNetworkEnabled();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!trusted(request.getRemoteAddr())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"UNTRUSTED_ROBOT_NETWORK\",\"message\":\"Robot upload endpoint is restricted\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean trusted(String address) {
        return Arrays.stream(properties.getFile().getTrustedRobotCidrs().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(cidr -> contains(cidr, address));
    }

    private boolean contains(String cidr, String address) {
        try {
            String[] values = cidr.split("/", 2);
            byte[] network = InetAddress.getByName(values[0]).getAddress();
            byte[] candidate = InetAddress.getByName(address).getAddress();
            if (network.length != candidate.length) {
                return false;
            }
            int prefix = values.length == 1 ? network.length * 8 : Integer.parseInt(values[1]);
            for (int index = 0; index < network.length; index++) {
                int bits = Math.min(8, Math.max(0, prefix - index * 8));
                if (bits == 0) {
                    return true;
                }
                int mask = (0xff << (8 - bits)) & 0xff;
                if ((network[index] & mask) != (candidate[index] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
