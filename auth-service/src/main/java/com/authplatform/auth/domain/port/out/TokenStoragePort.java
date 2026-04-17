package com.authplatform.auth.domain.port.out;

import com.authplatform.auth.domain.model.Token;

import java.time.Duration;
import java.util.Optional;

public interface TokenStoragePort {
    void save(Token token, Duration ttl);
    Optional<Token> findById(String tokenId);
    void revoke(String tokenId);
    boolean isRevoked(String tokenId);
    void revokeAllForUser(String username);
}
