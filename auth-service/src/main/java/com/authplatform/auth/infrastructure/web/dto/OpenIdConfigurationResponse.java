package com.authplatform.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenIdConfigurationResponse(
    String issuer,
    @JsonProperty("jwks_uri")
    String jwksUri,
    @JsonProperty("id_token_signing_alg_values_supported")
    List<String> idTokenSigningAlgValuesSupported,
    @JsonProperty("response_types_supported")
    List<String> responseTypesSupported,
    @JsonProperty("subject_types_supported")
    List<String> subjectTypesSupported,
    @JsonProperty("claims_supported")
    List<String> claimsSupported
) {}
