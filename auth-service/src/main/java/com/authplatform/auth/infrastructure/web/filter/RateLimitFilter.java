package com.authplatform.auth.infrastructure.web.filter;

import com.authplatform.auth.infrastructure.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String ip = clientIp(request);
            if (exceeded("ip:" + ip, properties.getIpLimit())) {
                reject(response, "ip");
                return;
            }

            String applicationId = applicationId(request);
            if (applicationId != null && exceeded("app:" + applicationId, properties.getApplicationLimit())) {
                reject(response, "applicationId");
                return;
            }
        } catch (Exception ex) {
            log.warn("Rate limit check failed open for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean exceeded(String subject, int limit) {
        String key = "rate-limit:auth-service:" + sanitize(subject);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }
        return count != null && count > limit;
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
            || uri.startsWith("/swagger-ui")
            || uri.startsWith("/v3/api-docs")
            || uri.startsWith("/.well-known");
    }

    private String applicationId(HttpServletRequest request) {
        String header = request.getHeader("X-Application-Id");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String parameter = request.getParameter("applicationId");
        return parameter == null || parameter.isBlank() ? null : parameter.trim();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9:._-]", "_");
    }

    private void reject(HttpServletResponse response, String subject) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Rate limit exceeded\",\"subject\":\"" + subject + "\"}");
    }
}
