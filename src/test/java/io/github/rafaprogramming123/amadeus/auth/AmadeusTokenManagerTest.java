package io.github.rafaprogramming123.amadeus.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmadeusTokenManagerTest {

    private static final Duration SKEW = Duration.ofSeconds(60);

    /** Fetcher that counts calls and returns tok-1, tok-2, … */
    private static final class CountingFetcher implements TokenFetcher {
        final AtomicInteger calls = new AtomicInteger();
        final long expiresIn;
        final long delayMillis;

        CountingFetcher(long expiresIn, long delayMillis) {
            this.expiresIn = expiresIn;
            this.delayMillis = delayMillis;
        }

        @Override
        public TokenResponse fetch() {
            int n = calls.incrementAndGet();
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return new TokenResponse("tok-" + n, "Bearer", expiresIn);
        }
    }

    /** Clock we can move forward by hand. */
    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-21T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void manyConcurrentCallersTriggerExactlyOneFetch() throws Exception {
        var fetcher = new CountingFetcher(1799, 150);   // slow fetch widens the race window
        var manager = new AmadeusTokenManager(fetcher, SKEW, Clock.systemUTC());
        var start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return manager.getToken();
                }));
            }
            start.countDown();   // release all 200 at once

            Set<String> tokens = new HashSet<>();
            for (Future<String> f : results) {
                tokens.add(f.get());
            }
            assertThat(tokens).containsExactly("tok-1");
        }
        assertThat(fetcher.calls).hasValue(1);
    }

    @Test
    void cachedTokenIsReusedUntilItEntersTheSkewWindow() {
        var clock = new MutableClock();
        var fetcher = new CountingFetcher(120, 0);   // valid 120s, refreshed 60s early
        var manager = new AmadeusTokenManager(fetcher, SKEW, clock);

        assertThat(manager.getToken()).isEqualTo("tok-1");

        clock.advance(Duration.ofSeconds(59));
        assertThat(manager.getToken()).isEqualTo("tok-1");
        assertThat(fetcher.calls).hasValue(1);

        clock.advance(Duration.ofSeconds(2));         // 61s: within 60s of expiry
        assertThat(manager.getToken()).isEqualTo("tok-2");
        assertThat(fetcher.calls).hasValue(2);
    }

    @Test
    void invalidateOnlyClearsTheRejectedToken() {
        var fetcher = new CountingFetcher(1799, 0);
        var manager = new AmadeusTokenManager(fetcher, SKEW, Clock.systemUTC());

        assertThat(manager.getToken()).isEqualTo("tok-1");

        manager.invalidate("some-old-token");          // stale 401 from an older token
        assertThat(manager.getToken()).isEqualTo("tok-1");
        assertThat(fetcher.calls).hasValue(1);

        manager.invalidate("tok-1");                   // the current one was rejected
        assertThat(manager.getToken()).isEqualTo("tok-2");
        assertThat(fetcher.calls).hasValue(2);
    }

    @Test
    void failedFetchCachesNothingAndNextCallRetries() {
        var calls = new AtomicInteger();
        TokenFetcher flaky = () -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("token endpoint down");
            return new TokenResponse("tok-ok", "Bearer", 1799);
        };
        var manager = new AmadeusTokenManager(flaky, SKEW, Clock.systemUTC());

        assertThatThrownBy(manager::getToken)
                .isInstanceOf(IllegalStateException.class);
        assertThat(manager.getToken()).isEqualTo("tok-ok");
    }
}
