package io.github.rafaprogramming123.amadeus.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the Amadeus access token for the whole application.
 *
 * Concurrency guarantees:
 *  - Fast path is lock-free: a volatile read of an immutable AccessToken.
 *  - Only ONE thread ever calls the token endpoint at a time ("single flight").
 *    Other threads that need a token wait on the lock, then re-check and reuse
 *    the token the first thread just fetched (double-checked locking).
 *  - invalidate() only clears the token if it is the SAME one that was rejected,
 *    so a slow 401 can't throw away a fresh token another thread just obtained.
 *  - If a fetch fails, nothing is cached; the next caller simply tries again.
 */
public class AmadeusTokenManager {

    private static final Logger log = LoggerFactory.getLogger(AmadeusTokenManager.class);

    private final TokenFetcher fetcher;
    private final Duration refreshSkew;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile AccessToken current;   // volatile => every thread sees the latest write

    public AmadeusTokenManager(TokenFetcher fetcher, Duration refreshSkew, Clock clock) {
        this.fetcher = fetcher;
        this.refreshSkew = refreshSkew;
        this.clock = clock;
    }

    /** Returns a usable token, fetching a new one only if needed. */
    public String getToken() {
        AccessToken token = current;                                    // 1) lock-free fast path
        if (token != null && token.isUsable(clock.instant(), refreshSkew)) {
            return token.value();
        }

        lock.lock();                                                    // 2) slow path: one refresher at a time
        try {
            token = current;                                            // 3) re-check after waiting
            if (token != null && token.isUsable(clock.instant(), refreshSkew)) {
                return token.value();
            }
            Instant requestedAt = clock.instant();                      // measured BEFORE the call => conservative expiry
            TokenResponse response = fetcher.fetch();                   // 4) we are the single refresher
            current = new AccessToken(response.accessToken(), requestedAt.plusSeconds(response.expiresIn()));
            log.debug("Fetched new Amadeus token, expires in {}s", response.expiresIn());
            return current.value();
        } finally {
            lock.unlock();
        }
    }

    /** Called after a 401. Compare-and-clear so we never wipe a newer token. */
    public void invalidate(String rejectedToken) {
        lock.lock();
        try {
            AccessToken token = current;
            if (token != null && token.value().equals(rejectedToken)) {
                current = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
