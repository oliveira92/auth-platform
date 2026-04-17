package com.authplatform.auth.infrastructure.config;

import com.authplatform.auth.domain.service.AuthenticationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public AuthenticationDomainService authenticationDomainService() {
        return new AuthenticationDomainService();
    }
}
