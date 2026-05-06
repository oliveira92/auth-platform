package com.authplatform.auth.infrastructure.ldap;

import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.infrastructure.config.LdapCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class LdapUserCache {

    private static final String USER_KEY_PREFIX = "ldap-cache:user:";
    private static final String USER_INDEX_PREFIX = "ldap-cache:users:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final LdapCacheProperties properties;

    public Optional<User> get(String domain, String username) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        try {
            Object cached = redisTemplate.opsForValue().get(userKey(domain, username));
            if (cached instanceof User user) {
                return Optional.of(user);
            }
        } catch (Exception ex) {
            log.warn("LDAP user cache read failed open for {}/{}: {}", domain, username, ex.getMessage());
        }

        return Optional.empty();
    }

    public void put(User user) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                userKey(user.ldapDomain(), user.username()),
                user,
                Duration.ofSeconds(properties.getUserTtlSeconds())
            );
            redisTemplate.opsForSet().add(userIndexKey(user.ldapDomain()), user.username());
        } catch (Exception ex) {
            log.warn("LDAP user cache write failed open for {}/{}: {}",
                user.ldapDomain(), user.username(), ex.getMessage());
        }
    }

    public Set<Object> cachedUsernames(String domain) {
        if (!properties.isEnabled() || !properties.isSyncEnabled()) {
            return Set.of();
        }

        try {
            Set<Object> usernames = redisTemplate.opsForSet().members(userIndexKey(domain));
            return usernames == null ? Set.of() : usernames;
        } catch (Exception ex) {
            log.warn("LDAP user cache index read failed open for {}: {}", domain, ex.getMessage());
            return Set.of();
        }
    }

    public void evict(String domain, String username) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            redisTemplate.delete(userKey(domain, username));
            redisTemplate.opsForSet().remove(userIndexKey(domain), username);
        } catch (Exception ex) {
            log.warn("LDAP user cache eviction failed open for {}/{}: {}", domain, username, ex.getMessage());
        }
    }

    private String userKey(String domain, String username) {
        return USER_KEY_PREFIX + sanitize(domain) + ":" + sanitize(username);
    }

    private String userIndexKey(String domain) {
        return USER_INDEX_PREFIX + sanitize(domain);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9@._-]", "_");
    }
}
