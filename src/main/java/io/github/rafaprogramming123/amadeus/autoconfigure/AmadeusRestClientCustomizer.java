package io.github.rafaprogramming123.amadeus.autoconfigure;

import org.springframework.web.client.RestClient;

/**
 * Lets an app tweak the RestClient used for Amadeus API calls (extra headers, interceptors, …)
 * without replacing the whole setup. Declare one or more as beans; they run in {@code @Order}.
 */
@FunctionalInterface
public interface AmadeusRestClientCustomizer {
    void customize(RestClient.Builder builder);
}
