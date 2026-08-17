package com.robot.bigscreen.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/control/files/*/hls/**").permitAll()
                        .requestMatchers("/api/**", "/ws/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver websocketResolver = new DefaultBearerTokenResolver();
        websocketResolver.setAllowUriQueryParameter(true);
        return request -> resolveBearerToken(request, headerResolver, websocketResolver);
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${bigscreen.auth.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                authorizedClientValidator(clientId));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    OAuth2TokenValidator<Jwt> authorizedClientValidator(String clientId) {
        return jwt -> {
            boolean authorizedPartyMatches = clientId.equals(jwt.getClaimAsString("azp"));
            boolean audienceMatches = jwt.getAudience() != null && jwt.getAudience().contains(clientId);
            if (authorizedPartyMatches || audienceMatches) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Token is not issued for the configured bigscreen client",
                    null);
            return OAuth2TokenValidatorResult.failure(error);
        };
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
