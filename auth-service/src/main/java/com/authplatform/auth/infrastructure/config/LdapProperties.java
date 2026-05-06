package com.authplatform.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "auth.ldap")
public class LdapProperties {
    public static final String DEFAULT_DOMAIN = "default";

    private String url;
    private String baseDn;
    private String userDn;
    private String password;
    private String defaultDomain = DEFAULT_DOMAIN;
    private String userSearchBase = "ou=users";
    private String userSearchFilter = "(uid={0})";
    private String groupSearchBase = "ou=groups";
    private String groupSearchFilter = "(memberUid={0})";
    private String groupRoleAttribute = "cn";
    // Attribute that uniquely identifies the user (uid for OpenLDAP, sAMAccountName for AD)
    private String usernameAttribute = "uid";
    // LDAP objectClass used for groups (posixGroup for OpenLDAP, group for AD)
    private String groupObjectClass = "posixGroup";
    // Attribute that links a user to a group (memberUid for OpenLDAP, member for AD)
    private String groupMemberAttribute = "memberUid";
    // When true, group member value is the username; when false, it is the user's full DN (AD)
    private boolean groupMemberIsUsername = true;
    private boolean referral = false;
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
    private Map<String, Domain> domains = new LinkedHashMap<>();

    public ResolvedDomain resolveDomain(String requestedDomain) {
        String key = normalizeDomain(requestedDomain);
        if (domains.isEmpty()) {
            return fromRoot(DEFAULT_DOMAIN);
        }

        Domain domain = domains.get(key);
        String resolvedKey = key;
        if (domain == null) {
            resolvedKey = normalizeDomain(defaultDomain);
            domain = domains.get(resolvedKey);
        }
        if (domain == null && domains.size() == 1) {
            Map.Entry<String, Domain> onlyDomain = domains.entrySet().iterator().next();
            resolvedKey = onlyDomain.getKey();
            domain = onlyDomain.getValue();
        }
        if (domain == null) {
            return fromRoot(DEFAULT_DOMAIN);
        }

        return new ResolvedDomain(
            resolvedKey,
            valueOrDefault(domain.getUrl(), url),
            valueOrDefault(domain.getBaseDn(), baseDn),
            valueOrDefault(domain.getUserDn(), userDn),
            valueOrDefault(domain.getPassword(), password),
            valueOrDefault(domain.getUserSearchBase(), userSearchBase),
            valueOrDefault(domain.getUserSearchFilter(), userSearchFilter),
            valueOrDefault(domain.getGroupSearchBase(), groupSearchBase),
            valueOrDefault(domain.getGroupSearchFilter(), groupSearchFilter),
            valueOrDefault(domain.getGroupRoleAttribute(), groupRoleAttribute),
            valueOrDefault(domain.getUsernameAttribute(), usernameAttribute),
            valueOrDefault(domain.getGroupObjectClass(), groupObjectClass),
            valueOrDefault(domain.getGroupMemberAttribute(), groupMemberAttribute),
            domain.getGroupMemberIsUsername() == null ? groupMemberIsUsername : domain.getGroupMemberIsUsername(),
            domain.getReferral() == null ? referral : domain.getReferral(),
            domain.getConnectTimeout() == null ? connectTimeout : domain.getConnectTimeout(),
            domain.getReadTimeout() == null ? readTimeout : domain.getReadTimeout()
        );
    }

    public String domainFromUsername(String username) {
        if (username == null || username.isBlank()) {
            return defaultDomain;
        }

        int separator = username.indexOf('\\');
        if (separator > 0) {
            return username.substring(0, separator);
        }

        int at = username.indexOf('@');
        if (at > 0) {
            String suffix = username.substring(at + 1);
            if (domains.containsKey(suffix)) {
                return suffix;
            }
        }

        return defaultDomain;
    }

    public String usernameWithoutDomainPrefix(String username) {
        if (username == null) {
            return null;
        }

        int separator = username.indexOf('\\');
        return separator > -1 && separator + 1 < username.length()
            ? username.substring(separator + 1)
            : username;
    }

    private ResolvedDomain fromRoot(String domain) {
        return new ResolvedDomain(
            normalizeDomain(domain),
            url,
            baseDn,
            userDn,
            password,
            userSearchBase,
            userSearchFilter,
            groupSearchBase,
            groupSearchFilter,
            groupRoleAttribute,
            usernameAttribute,
            groupObjectClass,
            groupMemberAttribute,
            groupMemberIsUsername,
            referral,
            connectTimeout,
            readTimeout
        );
    }

    private String normalizeDomain(String domain) {
        return domain == null || domain.isBlank() ? defaultDomain : domain.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Data
    public static class Domain {
        private String url;
        private String baseDn;
        private String userDn;
        private String password;
        private String userSearchBase;
        private String userSearchFilter;
        private String groupSearchBase;
        private String groupSearchFilter;
        private String groupRoleAttribute;
        private String usernameAttribute;
        private String groupObjectClass;
        private String groupMemberAttribute;
        private Boolean groupMemberIsUsername;
        private Boolean referral;
        private Integer connectTimeout;
        private Integer readTimeout;
    }

    public record ResolvedDomain(
        String key,
        String url,
        String baseDn,
        String userDn,
        String password,
        String userSearchBase,
        String userSearchFilter,
        String groupSearchBase,
        String groupSearchFilter,
        String groupRoleAttribute,
        String usernameAttribute,
        String groupObjectClass,
        String groupMemberAttribute,
        boolean groupMemberIsUsername,
        boolean referral,
        int connectTimeout,
        int readTimeout
    ) {}
}
