package com.authplatform.auth.infrastructure.ldap;

import com.authplatform.auth.infrastructure.config.LdapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class LdapTemplateFactory {

    private final LdapProperties ldapProperties;
    private final Map<String, LdapTemplate> templates = new ConcurrentHashMap<>();

    public LdapTemplate getTemplate(String domainKey) {
        LdapProperties.ResolvedDomain domain = ldapProperties.resolveDomain(domainKey);
        return templates.computeIfAbsent(domain.key(), ignored -> createTemplate(domain));
    }

    public LdapProperties.ResolvedDomain resolveDomain(String domainKey) {
        return ldapProperties.resolveDomain(domainKey);
    }

    public String resolveDomainKey(String requestedDomain, String username) {
        if (requestedDomain != null && !requestedDomain.isBlank()) {
            return requestedDomain.trim();
        }
        return ldapProperties.domainFromUsername(username);
    }

    public String normalizeUsername(String username) {
        return ldapProperties.usernameWithoutDomainPrefix(username);
    }

    private LdapTemplate createTemplate(LdapProperties.ResolvedDomain domain) {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(domain.url());
        contextSource.setBase(domain.baseDn());
        contextSource.setUserDn(domain.userDn());
        contextSource.setPassword(domain.password());
        contextSource.setReferral(domain.referral() ? "follow" : "ignore");

        Map<String, Object> baseEnv = new HashMap<>();
        baseEnv.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(domain.connectTimeout()));
        baseEnv.put("com.sun.jndi.ldap.read.timeout", String.valueOf(domain.readTimeout()));
        contextSource.setBaseEnvironmentProperties(baseEnv);
        contextSource.afterPropertiesSet();

        LdapTemplate template = new LdapTemplate(contextSource);
        template.setIgnorePartialResultException(true);
        return template;
    }
}
