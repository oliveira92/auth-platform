package com.authplatform.auth.domain.port.in;

public record RefreshTokenCommand(
    String refreshToken,
    String applicationId,
    String clientIp
) {}
