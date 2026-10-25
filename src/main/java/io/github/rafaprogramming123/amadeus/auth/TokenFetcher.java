package io.github.rafaprogramming123.amadeus.auth;

/**
 * Whatever actually calls the token endpoint. Separated from AmadeusTokenManager
 * so the caching/locking logic can be unit-tested without HTTP.
 */
@FunctionalInterface
public interface TokenFetcher {
    TokenResponse fetch();
}
