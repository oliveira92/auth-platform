package com.authplatform.auth.domain.model;

public record TokenPair(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresIn,
    long refreshTokenExpiresIn,
    String tokenType
) {
    public static TokenPair of(String accessToken, String refreshToken,
                                long accessExpiresIn, long refreshExpiresIn) {
        return new TokenPair(accessToken, refreshToken, accessExpiresIn, refreshExpiresIn, "Bearer");
    }
}
