package com.authplatform.auth.infrastructure.web.dto;

public record JsonWebKeyResponse(
    String kty,
    String use,
    String alg,
    String kid,
    String n,
    String e
) {}
