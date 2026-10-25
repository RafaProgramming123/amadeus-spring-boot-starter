package io.github.rafaprogramming123.amadeus.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Shape of the OAuth2 token endpoint response (extra fields are ignored). */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {}
