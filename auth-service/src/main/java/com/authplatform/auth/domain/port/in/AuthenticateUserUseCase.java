package com.authplatform.auth.domain.port.in;

import com.authplatform.auth.domain.model.TokenPair;

public interface AuthenticateUserUseCase {
    TokenPair authenticate(AuthenticateUserCommand command);
}
