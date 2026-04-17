package com.authplatform.gateway.filter;

import com.authplatform.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/validate",
        "/actuator/health"
    );

    private final GatewayProperties gatewayProperties;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.publicKey = loadPublicKey(gatewayProperties.getJwtPublicKey());
            log.info("JWT public key loaded into API Gateway");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT public key", e);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // Forward user identity in headers for downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Username", claims.getSubject())
                .header("X-User-Roles", String.join(",", getRoles(claims)))
                .header("X-User-Groups", String.join(",", getGroups(claims)))
                .header("X-Application-Id", getStringClaim(claims, "applicationId", ""))
                .header("X-Token-Id", claims.getId())
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "Token expired");
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith) ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-ui");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"error":"unauthorized","message":"%s"}
            """.formatted(message);
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @SuppressWarnings("unchecked")
    private List<String> getRoles(Claims claims) {
        Object roles = claims.get("roles");
        return roles instanceof List<?> l ? (List<String>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> getGroups(Claims claims) {
        Object groups = claims.get("groups");
        return groups instanceof List<?> l ? (List<String>) l : List.of();
    }

    private String getStringClaim(Claims claims, String key, String defaultValue) {
        Object value = claims.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = pemReader.readPemObject();
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pemObject.getContent());
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        }
    }
}
