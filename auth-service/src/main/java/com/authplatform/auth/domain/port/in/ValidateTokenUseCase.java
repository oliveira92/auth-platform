package com.authplatform.auth.domain.port.in;

import com.authplatform.auth.domain.model.Token;

import java.util.Optional;

public interface ValidateTokenUseCase {
    Optional<Token> validate(String rawToken);
}
