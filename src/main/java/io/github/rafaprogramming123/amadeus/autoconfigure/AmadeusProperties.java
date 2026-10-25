package io.github.rafaprogramming123.amadeus.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Everything a consuming app can set under {@code amadeus.*}.
 * Only client-id and client-secret are required; the rest has sensible defaults.
 * (A library can't ship an application.yml, so defaults live here.)
 *
 * @param baseUrl      Enterprise API host; switch to production when you go live
 * @param tokenPath    OAuth2 token endpoint path
 * @param clientId     API key from Amadeus Enterprise
 * @param clientSecret API secret from Amadeus Enterprise
 * @param refreshSkew  refresh the token this long before it actually expires
 * @param connectTimeout TCP connect timeout
 * @param readTimeout  response timeout
 * @param retry        retry behaviour for 429 / transient errors
 * @param problemDetails turn AmadeusException into RFC 9457 responses in web apps
 */
@Validated
@ConfigurationProperties(prefix = "amadeus")
public record AmadeusProperties(
        @DefaultValue("https://test.travel.api.amadeus.com") @NotBlank String baseUrl,
        @DefaultValue("/v1/security/oauth2/token") @NotBlank String tokenPath,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @DefaultValue("60s") @NotNull Duration refreshSkew,
        @DefaultValue("5s") @NotNull Duration connectTimeout,
        @DefaultValue("30s") @NotNull Duration readTimeout,
        @DefaultValue @NotNull @Valid Retry retry,
        @DefaultValue @NotNull ProblemDetails problemDetails
) {
    /**
     * @param maxAttempts    total tries including the first one (3 = 1 call + 2 retries)
     * @param initialBackoff wait before the first retry; doubles each time, with jitter
     * @param maxBackoff     upper bound for any single wait, including Retry-After
     */
    public record Retry(
            @DefaultValue("3") @Min(1) int maxAttempts,
            @DefaultValue("300ms") @NotNull Duration initialBackoff,
            @DefaultValue("5s") @NotNull Duration maxBackoff
    ) {}

    /** @param enabled register the @RestControllerAdvice that maps AmadeusException */
    public record ProblemDetails(@DefaultValue("true") boolean enabled) {}
}
