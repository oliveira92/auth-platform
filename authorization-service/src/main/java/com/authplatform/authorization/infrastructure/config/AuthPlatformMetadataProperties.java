package com.authplatform.authorization.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.platform")
public class AuthPlatformMetadataProperties {
    private String issuer = "auth-platform";
    private String jwksUri = "http://localhost:8081/.well-known/jwks.json";
    private String introspectionUrl = "http://localhost:8081/api/v1/auth/validate";
    private String tokenAlgorithm = "RS256";
}
