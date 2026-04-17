package com.authplatform.auth.infrastructure.redis;

import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
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
public class RedisTokenStorageAdapter implements TokenStoragePort {

    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final String REVOKED_KEY_PREFIX = "revoked:";
    private static final String USER_TOKENS_KEY_PREFIX = "user_tokens:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void save(Token token, Duration ttl) {
        String tokenKey = TOKEN_KEY_PREFIX + token.tokenId();
        redisTemplate.opsForValue().set(tokenKey, token, ttl);

        // Track tokens by user for bulk revocation
        String userTokensKey = USER_TOKENS_KEY_PREFIX + token.username();
        redisTemplate.opsForSet().add(userTokensKey, token.tokenId());
        redisTemplate.expire(userTokensKey, ttl);

        log.debug("Saved token {} for user {}", token.tokenId(), token.username());
    }

    @Override
    public Optional<Token> findById(String tokenId) {
        Object value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + tokenId);
        return Optional.ofNullable(value instanceof Token t ? t : null);
    }

    @Override
    public void revoke(String tokenId) {
        redisTemplate.opsForValue().set(
            REVOKED_KEY_PREFIX + tokenId,
            "revoked",
            Duration.ofDays(30)
        );
        redisTemplate.delete(TOKEN_KEY_PREFIX + tokenId);
        log.debug("Revoked token {}", tokenId);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_KEY_PREFIX + tokenId));
    }

    @Override
    public void revokeAllForUser(String username) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + username;
        Set<Object> tokenIds = redisTemplate.opsForSet().members(userTokensKey);

        if (tokenIds != null) {
            tokenIds.forEach(tokenId -> revoke(tokenId.toString()));
        }
        redisTemplate.delete(userTokensKey);
        log.info("All tokens revoked for user {}", username);
    }
}
