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
        filter.and(new EqualsFilter("sAMAccountName", username));

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
        filter.and(new EqualsFilter("sAMAccountName", username));

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
            String userDn = resolveDn(user.username());
            AndFilter filter = new AndFilter();
            filter.and(new EqualsFilter("objectClass", "group"));
            filter.and(new EqualsFilter("member", userDn));

            return ldapTemplate.search(
                ldapProperties.getGroupSearchBase(),
                filter.toString(),
                (AttributesMapper<String>) attrs -> {
                    Object cn = attrs.get(ldapProperties.getGroupRoleAttribute());
                    return cn != null ? cn.toString() : null;
                }
            ).stream().filter(g -> g != null).toList();
        } catch (Exception e) {
            log.warn("Could not fetch groups for user {}: {}", user.username(), e.getMessage());
            return List.of();
        }
    }

    private String resolveDn(String username) {
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("sAMAccountName", username));
        List<String> dns = ldapTemplate.search(
            ldapProperties.getUserSearchBase(),
            filter.toString(),
            (AttributesMapper<String>) attrs -> attrs.get("distinguishedName") != null
                ? attrs.get("distinguishedName").get().toString()
                : null
        );
        return dns.isEmpty() ? username : dns.get(0);
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
            String username = getAttr(attrs, "sAMAccountName", fallbackUsername);
            String email = getAttr(attrs, "mail", "");
            String displayName = getAttr(attrs, "displayName", username);
            String department = getAttr(attrs, "department", "");

            attributes.put("employeeId", getAttr(attrs, "employeeID", ""));
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
