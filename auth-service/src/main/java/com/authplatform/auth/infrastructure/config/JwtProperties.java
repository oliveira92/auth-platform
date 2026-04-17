package com.authplatform.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {
    private String privateKey;
    private String publicKey;
    private long accessTokenExpirationSeconds = 900;        // 15 minutes
    private long refreshTokenExpirationSeconds = 86400;     // 24 hours
    private String issuer = "auth-platform";
}
