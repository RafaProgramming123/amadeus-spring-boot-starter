package io.github.rafaprogramming123.amadeus.http;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(1));

    private static MockClientHttpRequest request(HttpMethod method) {
        return new MockClientHttpRequest(method, URI.create("https://test.travel.api.amadeus.com/x"));
    }

    private static MockClientHttpRequest searchPost() {
        var r = request(HttpMethod.POST);
        r.getHeaders().set(RetryPolicy.METHOD_OVERRIDE, "GET");
        return r;
    }

    @Test
    void searchAndPricingPostsCountAsIdempotent() {
        assertThat(policy.isIdempotent(searchPost())).isTrue();
        assertThat(policy.isIdempotent(request(HttpMethod.GET))).isTrue();
        assertThat(policy.isIdempotent(request(HttpMethod.DELETE))).isTrue();
    }

    @Test
    void bookingPostAndPatchAreNotIdempotent() {
        assertThat(policy.isIdempotent(request(HttpMethod.POST))).isFalse();
        assertThat(policy.isIdempotent(request(HttpMethod.PATCH))).isFalse();
    }

    @Test
    void rateLimitIsRetriedEvenForBookings() {
        assertThat(policy.shouldRetry(request(HttpMethod.POST), 429, 1)).isTrue();
    }

    @Test
    void serverErrorsAreRetriedOnlyWhenIdempotent() {
        assertThat(policy.shouldRetry(searchPost(), 503, 1)).isTrue();
        assertThat(policy.shouldRetry(request(HttpMethod.POST), 503, 1)).isFalse();
    }

    @Test
    void clientErrorsAreNeverRetried() {
        assertThat(policy.shouldRetry(searchPost(), 400, 1)).isFalse();
        assertThat(policy.shouldRetry(searchPost(), 404, 1)).isFalse();
    }

    @Test
    void stopsAtMaxAttempts() {
        assertThat(policy.shouldRetry(searchPost(), 503, 2)).isTrue();
        assertThat(policy.shouldRetry(searchPost(), 503, 3)).isFalse();
        assertThat(policy.shouldRetryAfterIoError(searchPost(), 3)).isFalse();
    }

    @Test
    void backoffGrowsExponentiallyWithJitterAndIsCapped() {
        for (int i = 0; i < 50; i++) {
            assertThat(policy.backoff(1, null)).isBetween(Duration.ofMillis(50), Duration.ofMillis(100));
            assertThat(policy.backoff(2, null)).isBetween(Duration.ofMillis(100), Duration.ofMillis(200));
            assertThat(policy.backoff(10, null)).isBetween(Duration.ofMillis(500), Duration.ofSeconds(1));
        }
    }

    @Test
    void retryAfterHeaderWinsButIsCapped() {
        assertThat(policy.backoff(1, "0")).isEqualTo(Duration.ZERO);
        assertThat(policy.backoff(1, "30")).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.backoff(1, "Wed, 21 Oct 2026 07:28:00 GMT"))   // HTTP-date → exponential fallback
                .isBetween(Duration.ofMillis(50), Duration.ofMillis(100));
    }
}
