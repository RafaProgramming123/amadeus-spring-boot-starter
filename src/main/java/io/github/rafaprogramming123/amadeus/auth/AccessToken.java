package io.github.rafaprogramming123.amadeus.auth;

import java.time.Duration;
import java.time.Instant;

/** Immutable token snapshot. Immutability is what makes it safe to share across threads. */
public record AccessToken(String value, Instant expiresAt) {

    /** Valid only if it will still be valid {@code skew} from now — avoids sending a token that dies in flight. */
    public boolean isUsable(Instant now, Duration skew) {
        return now.plus(skew).isBefore(expiresAt);
    }
}
