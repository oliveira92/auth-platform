package com.authplatform.auth.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LdapPropertiesTest {

    @Test
    void shouldResolveRootConfigurationWhenNoNamedDomainsExist() {
        LdapProperties properties = defaultProperties();

        LdapProperties.ResolvedDomain domain = properties.resolveDomain("corp");

        assertEquals("default", domain.key());
        assertEquals("ldap://default", domain.url());
        assertEquals("uid", domain.usernameAttribute());
    }

    @Test
    void shouldResolveNamedDomainWithFallbacksFromRootConfiguration() {
        LdapProperties properties = defaultProperties();
        LdapProperties.Domain corp = new LdapProperties.Domain();
        corp.setUrl("ldaps://corp.example.com:636");
        corp.setBaseDn("dc=corp,dc=example,dc=com");
        corp.setUsernameAttribute("sAMAccountName");
        corp.setGroupMemberIsUsername(false);
        properties.getDomains().put("corp", corp);

        LdapProperties.ResolvedDomain domain = properties.resolveDomain("corp");

        assertEquals("corp", domain.key());
        assertEquals("ldaps://corp.example.com:636", domain.url());
        assertEquals("dc=corp,dc=example,dc=com", domain.baseDn());
        assertEquals("cn=admin,dc=default", domain.userDn());
        assertEquals("sAMAccountName", domain.usernameAttribute());
        assertEquals(false, domain.groupMemberIsUsername());
    }

    @Test
    void shouldInferDomainFromDomainPrefixedUsername() {
        LdapProperties properties = defaultProperties();

        assertEquals("corp", properties.domainFromUsername("corp\\john.doe"));
        assertEquals("john.doe", properties.usernameWithoutDomainPrefix("corp\\john.doe"));
    }

    private LdapProperties defaultProperties() {
        LdapProperties properties = new LdapProperties();
        properties.setUrl("ldap://default");
        properties.setBaseDn("dc=default");
        properties.setUserDn("cn=admin,dc=default");
        properties.setPassword("admin");
        properties.setUsernameAttribute("uid");
        return properties;
    }
}
