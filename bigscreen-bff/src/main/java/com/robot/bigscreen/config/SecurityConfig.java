package com.robot.bigscreen.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** 按 JWT 客户端及路由配置大屏和现场应用的访问边界。 */
@Configuration
public class SecurityConfig {

    /**
     * 配置 JWT 验签、客户端路由白名单及文档默认隔离规则。
     *
     * @param http Spring Security HTTP 安全配置构建器
     * @param bearerTokenResolver 当前请求的 Bearer 令牌解析器
     * @param bigscreenClientId 允许使用大屏接口的 JWT 客户端标识
     * @param fieldCallClientId 允许现场呼叫的 JWT 客户端标识
     * @return 当前服务使用的安全过滤链
     * @throws Exception 安全过滤链构建失败时抛出，应用启动应失败而非绕过鉴权
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            @Value("${bigscreen.auth.client-id}") String bigscreenClientId,
            @Value("${bigscreen.auth.field-call-client-id}") String fieldCallClientId) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/control/files/*/hls/**").permitAll()
                        .requestMatchers("/ws/field-call").access(clientAuthorization(fieldCallClientId))
                        // field-app 复用大屏 BFF 的告警 REST 和实时通道；其他大屏接口仍只允许 bigscreen-web。
                        .requestMatchers(
                                "/api/bigscreen/panorama/overview",
                                "/api/bigscreen/panorama/alarms",
                                "/api/bigscreen/panorama/alarms/**",
                                "/api/bigscreen/control/files/*/download-url",
                                "/ws/bigscreen")
                        .access(clientAuthorization(bigscreenClientId, fieldCallClientId))
                        .requestMatchers("/api/**", "/ws/**").access(clientAuthorization(bigscreenClientId))
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * 解析浏览器 Bearer 凭证；仅对允许的 WebSocket 和媒体正文路径接受查询参数令牌。
     * @return 当前路由约束下的令牌解析器
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver websocketResolver = new DefaultBearerTokenResolver();
        websocketResolver.setAllowUriQueryParameter(true);
        return request -> resolveBearerToken(request, headerResolver, websocketResolver);
    }

    /**
     * 创建与配置签发者及公钥集合绑定的 JWT 解码器。
     *
     * @param issuerUri JWT 签发者地址
     * @param jwkSetUri 用于验证 JWT 的公钥集合地址
     * @param clientId 客户端 ID
     * @param fieldCallClientId 允许现场呼叫的 JWT 客户端标识
     * @return 用于验证浏览器 JWT 的解码器
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${bigscreen.auth.client-id}") String clientId,
            @Value("${bigscreen.auth.field-call-client-id}") String fieldCallClientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                authorizedClientValidator(clientId, fieldCallClientId));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    OAuth2TokenValidator<Jwt> authorizedClientValidator(String... clientIds) {
        return jwt -> {
            for (String clientId : clientIds) {
                if (tokenIssuedForClient(jwt, clientId)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Token is not issued for a configured client",
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    private AuthorizationManager<RequestAuthorizationContext> clientAuthorization(String... clientIds) {
        return (authentication, context) -> {
            if (authentication.get() instanceof JwtAuthenticationToken jwtAuthentication) {
                for (String clientId : clientIds) {
                    if (tokenIssuedForClient(jwtAuthentication.getToken(), clientId)) {
                        return new AuthorizationDecision(true);
                    }
                }
            }
            return new AuthorizationDecision(false);
        };
    }

    private boolean tokenIssuedForClient(Jwt jwt, String clientId) {
        return clientId.equals(jwt.getClaimAsString("azp"))
                || (jwt.getAudience() != null && jwt.getAudience().contains(clientId));
    }

    private String resolveBearerToken(
            HttpServletRequest request,
            DefaultBearerTokenResolver headerResolver,
            DefaultBearerTokenResolver websocketResolver) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/ws/") || isFileContentRead(uri)) {
            return websocketResolver.resolve(request);
        }
        return headerResolver.resolve(request);
    }

    private boolean isFileContentRead(String uri) {
        return uri.startsWith("/api/bigscreen/control/files/") && uri.endsWith("/content");
    }
}
