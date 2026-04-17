package com.authplatform.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.gateway")
public class GatewayProperties {
    private String jwtPublicKey;
    private String jwtIssuer = "auth-platform";
}
