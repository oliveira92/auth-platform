package com.authplatform.auth.infrastructure.ldap;

import com.authplatform.auth.domain.exception.UserNotFoundException;
import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.domain.port.out.LdapUserPort;
import com.authplatform.auth.infrastructure.config.LdapCacheProperties;
import com.authplatform.auth.infrastructure.config.LdapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LdapUserAdapter implements LdapUserPort {

    private final LdapProperties ldapProperties;
    private final LdapCacheProperties ldapCacheProperties;
    private final LdapUserCache ldapUserCache;
    private final LdapTemplateFactory ldapTemplateFactory;

    @Override
    public User authenticate(String username, String password, String requestedDomain) {
        String domainKey = ldapTemplateFactory.resolveDomainKey(requestedDomain, username);
        String normalizedUsername = ldapTemplateFactory.normalizeUsername(username);
        LdapProperties.ResolvedDomain domain = ldapTemplateFactory.resolveDomain(domainKey);
        LdapTemplate ldapTemplate = ldapTemplateFactory.getTemplate(domain.key());

        log.debug("Authenticating user {} via LDAP domain {}", normalizedUsername, domain.key());

        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter(domain.usernameAttribute(), normalizedUsername));

        boolean authenticated = ldapTemplate.authenticate(
            domain.userSearchBase(),
            filter.toString(),
            password
        );

        if (!authenticated) {
            log.warn("LDAP authentication failed for user: {} on domain: {}", normalizedUsername, domain.key());
            throw new com.authplatform.auth.domain.exception.AuthenticationException(
                "Invalid credentials for user: " + normalizedUsername
            );
        }

        return findByUsername(normalizedUsername, domain.key())
            .orElseThrow(() -> new UserNotFoundException(normalizedUsername));
    }

    @Override
    public Optional<User> findByUsername(String username, String requestedDomain) {
        String domainKey = ldapTemplateFactory.resolveDomainKey(requestedDomain, username);
        String normalizedUsername = ldapTemplateFactory.normalizeUsername(username);
        LdapProperties.ResolvedDomain domain = ldapTemplateFactory.resolveDomain(domainKey);

        log.debug("Looking up LDAP user {} on domain {}", normalizedUsername, domain.key());
        Optional<User> cachedUser = ldapUserCache.get(domain.key(), normalizedUsername);
        if (cachedUser.isPresent()) {
            log.debug("LDAP user {}/{} resolved from Redis cache", domain.key(), normalizedUsername);
            return cachedUser;
        }

        Optional<User> user = findByUsernameFromLdap(normalizedUsername, domain);
        user.ifPresent(ldapUserCache::put);
        return user;
    }

    @Scheduled(
        fixedDelayString = "${auth.ldap.cache.sync-interval-ms:300000}",
        initialDelayString = "${auth.ldap.cache.sync-initial-delay-ms:60000}"
    )
    public void synchronizeCachedUsers() {
        if (!ldapCacheProperties.isEnabled() || !ldapCacheProperties.isSyncEnabled()) {
            return;
        }

        for (String domainKey : ldapProperties.getDomains().isEmpty()
            ? List.of(LdapProperties.DEFAULT_DOMAIN)
            : ldapProperties.getDomains().keySet()) {
            LdapProperties.ResolvedDomain domain = ldapTemplateFactory.resolveDomain(domainKey);
            Set<Object> cachedUsernames = ldapUserCache.cachedUsernames(domain.key());
            if (cachedUsernames.isEmpty()) {
                continue;
            }

            log.debug("Synchronizing {} LDAP cached user(s) on domain {}", cachedUsernames.size(), domain.key());
            for (Object cachedUsername : cachedUsernames) {
                if (cachedUsername == null) {
                    continue;
                }
                String username = cachedUsername.toString();
                findByUsernameFromLdap(username, domain)
                    .ifPresentOrElse(ldapUserCache::put, () -> ldapUserCache.evict(domain.key(), username));
            }
        }
    }

    private Optional<User> findByUsernameFromLdap(String username, LdapProperties.ResolvedDomain domain) {
        LdapTemplate ldapTemplate = ldapTemplateFactory.getTemplate(domain.key());
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter(domain.usernameAttribute(), username));

        List<User> users = ldapTemplate.search(
            domain.userSearchBase(),
            filter.toString(),
            SearchControls.SUBTREE_SCOPE,
            new UserAttributesMapper(username, domain)
        );

        if (users.isEmpty()) {
            return Optional.empty();
        }

        User user = users.get(0);
        List<String> groups = findGroupsForUser(user, domain);
        return Optional.of(new User(
            user.username(), user.email(), user.displayName(),
            user.department(), groups, mapGroupsToRoles(groups), user.attributes(), domain.key()
        ));
    }

    private List<String> findGroupsForUser(User user, LdapProperties.ResolvedDomain domain) {
        try {
            LdapTemplate ldapTemplate = ldapTemplateFactory.getTemplate(domain.key());
            String memberValue = domain.groupMemberIsUsername()
                ? user.username()
                : resolveDn(user.username(), domain);

            AndFilter filter = new AndFilter();
            filter.and(new EqualsFilter("objectClass", domain.groupObjectClass()));
            filter.and(new EqualsFilter(domain.groupMemberAttribute(), memberValue));

            return ldapTemplate.search(
                domain.groupSearchBase(),
                filter.toString(),
                (AttributesMapper<String>) attrs -> {
                    try {
                        var attr = attrs.get(domain.groupRoleAttribute());
                        return attr != null && attr.get() != null ? attr.get().toString() : null;
                    } catch (NamingException e) {
                        return null;
                    }
                }
            ).stream().filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("Could not fetch groups for user {}: {}", user.username(), e.getMessage());
            return List.of();
        }
    }

    private String resolveDn(String username, LdapProperties.ResolvedDomain domain) {
        LdapTemplate ldapTemplate = ldapTemplateFactory.getTemplate(domain.key());
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter(domain.usernameAttribute(), username));
        List<String> dns = ldapTemplate.search(
            domain.userSearchBase(),
            filter.toString(),
            (AttributesMapper<String>) attrs -> {
                try {
                    var dn = attrs.get("distinguishedName");
                    return dn != null ? dn.get().toString() : null;
                } catch (NamingException e) {
                    return null;
                }
            }
        );
        return dns.stream().filter(Objects::nonNull).findFirst().orElse(username);
    }

    private List<String> mapGroupsToRoles(List<String> groups) {
        return groups.stream()
            .map(g -> "ROLE_" + g.toUpperCase().replace("-", "_").replace(" ", "_"))
            .toList();
    }

    private class UserAttributesMapper implements AttributesMapper<User> {
        private final String fallbackUsername;
        private final LdapProperties.ResolvedDomain domain;

        UserAttributesMapper(String fallbackUsername, LdapProperties.ResolvedDomain domain) {
            this.fallbackUsername = fallbackUsername;
            this.domain = domain;
        }

        @Override
        public User mapFromAttributes(Attributes attrs) throws NamingException {
            Map<String, String> attributes = new HashMap<>();
            String username = getAttr(attrs, domain.usernameAttribute(), fallbackUsername);
            String email = getAttr(attrs, "mail", "");
            String displayName = getAttr(attrs, "displayName", getAttr(attrs, "cn", username));
            String department = getAttr(attrs, "department", getAttr(attrs, "ou", ""));

            attributes.put("title", getAttr(attrs, "title", ""));
            attributes.put("telephoneNumber", getAttr(attrs, "telephoneNumber", ""));

            return new User(username, email, displayName, department, new ArrayList<>(), new ArrayList<>(), attributes, domain.key());
        }

        private String getAttr(Attributes attrs, String name, String defaultValue) {
            try {
                var attr = attrs.get(name);
                return attr != null ? attr.get().toString() : defaultValue;
            } catch (NamingException e) {
                return defaultValue;
            }
        }
    }
}
