package com.authplatform.auth.infrastructure.ldap;

import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.infrastructure.config.LdapCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LdapUserCacheTest {

    @Test
    void shouldReturnCachedUserWhenPresent() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        LdapUserCache cache = new LdapUserCache(redisTemplate, properties());
        User user = user();

        when(valueOperations.get("ldap-cache:user:default:john.doe")).thenReturn(user);

        Optional<User> cached = cache.get("default", "john.doe");

        assertTrue(cached.isPresent());
        assertEquals(user, cached.get());
    }

    @Test
    void shouldWriteUserWithTtlAndIndex() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        SetOperations<String, Object> setOperations = redisTemplate.opsForSet();
        LdapUserCache cache = new LdapUserCache(redisTemplate, properties());
        User user = user();

        cache.put(user);

        verify(valueOperations).set("ldap-cache:user:default:john.doe", user, Duration.ofSeconds(300));
        verify(setOperations).add("ldap-cache:users:default", "john.doe");
    }

    @Test
    void shouldNotTouchRedisWhenDisabled() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        LdapCacheProperties properties = properties();
        properties.setEnabled(false);
        LdapUserCache cache = new LdapUserCache(redisTemplate, properties);

        assertTrue(cache.get("default", "john.doe").isEmpty());
        cache.put(user());

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    void shouldReadCachedUsernameIndex() {
        RedisTemplate<String, Object> redisTemplate = mockRedisTemplate();
        SetOperations<String, Object> setOperations = redisTemplate.opsForSet();
        LdapUserCache cache = new LdapUserCache(redisTemplate, properties());

        when(setOperations.members("ldap-cache:users:default")).thenReturn(Set.of("john.doe"));

        assertEquals(Set.of("john.doe"), cache.cachedUsernames("default"));
    }

    private RedisTemplate<String, Object> mockRedisTemplate() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        clearInvocations(redisTemplate);
        return redisTemplate;
    }

    private LdapCacheProperties properties() {
        return new LdapCacheProperties();
    }

    private User user() {
        return new User(
            "john.doe",
            "john.doe@empresa.com",
            "John Doe",
            "Engineering",
            List.of("engineers"),
            List.of("ROLE_ENGINEERS"),
            Map.of()
        );
    }
}
