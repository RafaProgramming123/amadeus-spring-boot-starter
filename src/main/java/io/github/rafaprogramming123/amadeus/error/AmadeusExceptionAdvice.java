package io.github.rafaprogramming123.amadeus.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps AmadeusException to an RFC 9457 problem response for YOUR API's callers.
 * The point: an Amadeus problem must not leak as a confusing status.
 *  - Amadeus 400/404/422 (bad search input, offer gone) → same status, caller can fix it
 *  - Amadeus 401/403 (our credentials)                   → 502, it's our misconfiguration
 *  - Amadeus 429 (rate limited, retries exhausted)       → 503, try again later
 *  - Amadeus 5xx                                         → 502, upstream is down
 */
@RestControllerAdvice
public class AmadeusExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(AmadeusExceptionAdvice.class);

    @ExceptionHandler(AmadeusException.class)
    ProblemDetail handle(AmadeusException ex) {
        HttpStatus status;
        if (ex.isAuthError()) {
            status = HttpStatus.BAD_GATEWAY;
            log.error("Amadeus rejected our credentials: {}", ex.getMessage());
        } else if (ex.isRateLimited()) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            log.warn("Amadeus rate limit hit: {}", ex.getMessage());
        } else if (ex.isClientError()) {
            HttpStatus resolved = HttpStatus.resolve(ex.status());
            status = resolved != null ? resolved : HttpStatus.BAD_REQUEST;
            log.info("Amadeus rejected request: {}", ex.getMessage());
        } else {
            status = HttpStatus.BAD_GATEWAY;
            log.error("Amadeus server error: {}", ex.getMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, describe(ex));
        problem.setTitle("Flight provider error");
        problem.setProperty("upstreamStatus", ex.status());
        if (ex.isClientError() && !ex.isAuthError()) {
            problem.setProperty("issues", ex.issues());   // safe to show: they describe the caller's input
        }
        return problem;
    }

    private static String describe(AmadeusException ex) {
        if (ex.isAuthError()) return "The flight provider is temporarily unavailable.";
        if (ex.isRateLimited()) return "Too many requests to the flight provider, please retry shortly.";
        if (ex.isServerError()) return "The flight provider is temporarily unavailable.";
        return ex.issues().isEmpty() ? "The flight provider rejected the request." : ex.issues().getFirst().detail();
    }
}
