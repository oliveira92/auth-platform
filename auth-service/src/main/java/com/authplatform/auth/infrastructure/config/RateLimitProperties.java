package com.authplatform.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int windowSeconds = 60;
    private int ipLimit = 120;
    private int applicationLimit = 600;
}
