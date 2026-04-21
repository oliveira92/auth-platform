package com.authplatform.auth.infrastructure.ldap;

import com.authplatform.auth.domain.exception.UserNotFoundException;
import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.domain.port.out.LdapUserPort;
import com.authplatform.auth.infrastructure.config.LdapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LdapUserAdapter implements LdapUserPort {

    private final LdapTemplate ldapTemplate;
    private final LdapProperties ldapProperties;

    @Override
    public User authenticate(String username, String password) {
        log.debug("Authenticating user {} via LDAP", username);

        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter(ldapProperties.getUsernameAttribute(), username));

        boolean authenticated = ldapTemplate.authenticate(
            ldapProperties.getUserSearchBase(),
            filter.toString(),
            password
        );

        if (!authenticated) {
            log.warn("LDAP authentication failed for user: {}", username);
            throw new com.authplatform.auth.domain.exception.AuthenticationException(
                "Invalid credentials for user: " + username
            );
        }

        return findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("Looking up LDAP user: {}", username);

        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "person"));
        filter.and(new EqualsFilter(ldapProperties.getUsernameAttribute(), username));

        List<User> users = ldapTemplate.search(
            ldapProperties.getUserSearchBase(),
            filter.toString(),
            SearchControls.SUBTREE_SCOPE,
            new UserAttributesMapper(username)
        );

        if (users.isEmpty()) {
            return Optional.empty();
        }

        User user = users.get(0);
        List<String> groups = findGroupsForUser(user);
        return Optional.of(new User(
            user.username(), user.email(), user.displayName(),
            user.department(), groups, mapGroupsToRoles(groups), user.attributes()
        ));
    }

    private List<String> findGroupsForUser(User user) {
        try {
            String memberValue = ldapProperties.isGroupMemberIsUsername()
                ? user.username()
                : resolveDn(user.username());

            AndFilter filter = new AndFilter();
            filter.and(new EqualsFilter("objectClass", ldapProperties.getGroupObjectClass()));
            filter.and(new EqualsFilter(ldapProperties.getGroupMemberAttribute(), memberValue));

            return ldapTemplate.search(
                ldapProperties.getGroupSearchBase(),
                filter.toString(),
                (AttributesMapper<String>) attrs -> {
                    try {
                        var attr = attrs.get(ldapProperties.getGroupRoleAttribute());
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

    private String resolveDn(String username) {
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter(ldapProperties.getUsernameAttribute(), username));
        List<String> dns = ldapTemplate.search(
            ldapProperties.getUserSearchBase(),
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

        UserAttributesMapper(String fallbackUsername) {
            this.fallbackUsername = fallbackUsername;
        }

        @Override
        public User mapFromAttributes(Attributes attrs) throws NamingException {
            Map<String, String> attributes = new HashMap<>();
            String username = getAttr(attrs, ldapProperties.getUsernameAttribute(), fallbackUsername);
            String email = getAttr(attrs, "mail", "");
            String displayName = getAttr(attrs, "displayName", getAttr(attrs, "cn", username));
            String department = getAttr(attrs, "department", getAttr(attrs, "ou", ""));

            attributes.put("title", getAttr(attrs, "title", ""));
            attributes.put("telephoneNumber", getAttr(attrs, "telephoneNumber", ""));

            return new User(username, email, displayName, department, new ArrayList<>(), new ArrayList<>(), attributes);
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
