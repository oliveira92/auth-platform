package com.authplatform.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.ldap.cache")
public class LdapCacheProperties {
    private boolean enabled = true;
    private long userTtlSeconds = 300;
    private boolean syncEnabled = true;
    private long syncIntervalMs = 300000;
    private long syncInitialDelayMs = 60000;
}
