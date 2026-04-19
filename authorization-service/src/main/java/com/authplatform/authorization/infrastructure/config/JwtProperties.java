package com.authplatform.authorization.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {
    private String publicKey;
    private String issuer = "auth-platform";
}
