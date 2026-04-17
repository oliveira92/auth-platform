package com.authplatform.auth.domain.port.in;

public record AuthenticateUserCommand(
    String username,
    String password,
    String applicationId,
    String clientIp
) {
    public static AuthenticateUserCommand of(String username, String password,
                                              String applicationId, String clientIp) {
        return new AuthenticateUserCommand(username, password, applicationId, clientIp);
    }
}
