package com.authplatform.auth.domain.port.out;

import com.authplatform.auth.domain.model.Token;

public interface JwtPort {
    String generateToken(Token token);
    Token parseToken(String rawToken);
}
