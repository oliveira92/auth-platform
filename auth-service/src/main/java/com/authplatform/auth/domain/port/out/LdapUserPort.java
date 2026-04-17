package com.authplatform.auth.domain.port.out;

import com.authplatform.auth.domain.model.User;

import java.util.Optional;

public interface LdapUserPort {
    User authenticate(String username, String password);
    Optional<User> findByUsername(String username);
}
