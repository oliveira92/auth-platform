package com.authplatform.authorization.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.audit")
public class AuditProperties {
    private boolean enabled = false;
    private String baseUrl = "http://localhost:8083";
    private int connectTimeoutMs = 500;
    private int readTimeoutMs = 1000;
}
