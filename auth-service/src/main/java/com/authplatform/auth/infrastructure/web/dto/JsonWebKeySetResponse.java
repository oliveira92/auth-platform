package com.authplatform.auth.infrastructure.web.dto;

import java.util.List;

public record JsonWebKeySetResponse(
    List<JsonWebKeyResponse> keys
) {}
