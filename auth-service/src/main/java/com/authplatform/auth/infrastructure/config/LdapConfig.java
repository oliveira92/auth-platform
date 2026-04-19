package com.authplatform.auth.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class LdapConfig {

    private final LdapProperties ldapProperties;

    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapProperties.getUrl());
        contextSource.setBase(ldapProperties.getBaseDn());
        contextSource.setUserDn(ldapProperties.getUserDn());
        contextSource.setPassword(ldapProperties.getPassword());
        contextSource.setReferral(ldapProperties.isReferral() ? "follow" : "ignore");
        Map<String, Object> baseEnv = new HashMap<>();
        baseEnv.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapProperties.getConnectTimeout()));
        baseEnv.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldapProperties.getReadTimeout()));
        contextSource.setBaseEnvironmentProperties(baseEnv);
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource ldapContextSource) {
        LdapTemplate template = new LdapTemplate(ldapContextSource);
        template.setIgnorePartialResultException(true);
        return template;
    }
}
