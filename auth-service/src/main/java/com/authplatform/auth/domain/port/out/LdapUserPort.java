package com.authplatform.auth.domain.port.out;

import com.authplatform.auth.domain.model.User;

import java.util.Optional;

public interface LdapUserPort {
    default User authenticate(String username, String password) {
        return authenticate(username, password, null);
    }

    User authenticate(String username, String password, String ldapDomain);

    default Optional<User> findByUsername(String username) {
        return findByUsername(username, null);
    }

    Optional<User> findByUsername(String username, String ldapDomain);
}
