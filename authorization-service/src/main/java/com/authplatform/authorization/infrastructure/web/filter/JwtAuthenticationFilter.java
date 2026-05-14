package com.authplatform.authorization.infrastructure.web.filter;

import com.authplatform.authorization.infrastructure.jwt.JwtTokenValidator;
import com.authplatform.authorization.infrastructure.web.security.AuthPlatformPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtTokenValidator.validateToken(token);
            String username = claims.getSubject();

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Extract LDAP groups (raw: "engineers") and roles (prefixed: "ROLE_ENGINEERS")
                // emitted by auth-service at login time from the AD/LDAP directory.
                List<String> ldapGroups = claims.get("groups", List.class);
                List<String> ldapRoles  = claims.get("roles",  List.class);

                AuthPlatformPrincipal principal =
                    AuthPlatformPrincipal.of(username, ldapGroups, ldapRoles);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated '{}' with {} LDAP group(s): {}",
                    username, principal.ldapGroups().size(), principal.ldapGroups());
            }
        } catch (Exception e) {
            log.warn("JWT validation failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
