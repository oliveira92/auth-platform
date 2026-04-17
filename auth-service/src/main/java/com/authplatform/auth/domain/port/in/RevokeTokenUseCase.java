package com.authplatform.auth.domain.port.in;

public interface RevokeTokenUseCase {
    void revoke(String rawToken);
    void revokeAllForUser(String username);
}
