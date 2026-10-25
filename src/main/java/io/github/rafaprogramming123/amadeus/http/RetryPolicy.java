package io.github.rafaprogramming123.amadeus.http;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides WHEN a failed Amadeus call may be repeated and HOW LONG to wait.
 *
 * The key rule is idempotency: repeating a request must not do something twice.
 *  - 429 Too Many Requests: always retryable — Amadeus refused it, nothing happened.
 *  - 500/502/503/504 and network errors: only for idempotent calls. We can't know if
 *    the server already acted, so a booking (POST /flight-orders) is NEVER retried.
 *  - Search and pricing are POSTs carrying "X-HTTP-Method-Override: GET": they are
 *    read-only, so they count as idempotent.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public static final String METHOD_OVERRIDE = "X-HTTP-Method-Override";

    private static final Set<HttpMethod> IDEMPOTENT =
            Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.DELETE);

    private static final Set<Integer> TRANSIENT_5XX = Set.of(500, 502, 503, 504);

    public boolean isIdempotent(HttpRequest request) {
        if (IDEMPOTENT.contains(request.getMethod())) {
            return true;
        }
        return HttpMethod.POST.equals(request.getMethod())
                && "GET".equalsIgnoreCase(request.getHeaders().getFirst(METHOD_OVERRIDE));
    }

    /** May we retry after receiving this HTTP status on attempt number {@code attempt} (1-based)? */
    public boolean shouldRetry(HttpRequest request, int status, int attempt) {
        if (attempt >= maxAttempts) return false;
        if (status == 429) return true;
        return TRANSIENT_5XX.contains(status) && isIdempotent(request);
    }

    /** May we retry after a network error (timeout, connection reset)? */
    public boolean shouldRetryAfterIoError(HttpRequest request, int attempt) {
        return attempt < maxAttempts && isIdempotent(request);
    }

    /**
     * Wait before retry number {@code attempt}. Honors Retry-After (seconds) when present,
     * otherwise exponential backoff with jitter so many threads don't retry in lockstep.
     */
    public Duration backoff(int attempt, String retryAfterHeader) {
        Duration retryAfter = parseRetryAfter(retryAfterHeader);
        if (retryAfter != null) {
            return min(retryAfter, maxBackoff);
        }
        long exp = initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 20));
        long cap = Math.min(exp, maxBackoff.toMillis());
        long jittered = cap / 2 + ThreadLocalRandom.current().nextLong(cap / 2 + 1);   // in [cap/2, cap]
        return Duration.ofMillis(jittered);
    }

    private static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) return null;
        try {
            long seconds = Long.parseLong(header.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException httpDate) {
            return null;   // HTTP-date form: fall back to exponential backoff
        }
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
