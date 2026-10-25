package io.github.rafaprogramming123.amadeus.http;

import io.github.rafaprogramming123.amadeus.auth.AmadeusTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;

/**
 * The ONE interceptor on the Amadeus RestClient. Per call it:
 *  1. attaches "Authorization: Bearer <token>" (fresh from the token manager every attempt)
 *  2. on 401: invalidates that exact token and retries once with a new one
 *  3. on 429 / transient 5xx / network error: waits and retries per {@link RetryPolicy}
 *  4. otherwise returns the response; any remaining 4xx/5xx becomes an AmadeusException
 *     in the status handler
 *
 * Auth and retry live in one loop on purpose: with two interceptors, a retry would
 * re-run only the interceptors after it, and could resend an old token.
 */
public class AmadeusRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AmadeusRequestInterceptor.class);

    private final AmadeusTokenManager tokens;
    private final RetryPolicy retryPolicy;

    public AmadeusRequestInterceptor(AmadeusTokenManager tokens, RetryPolicy retryPolicy) {
        this.tokens = tokens;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        boolean tokenAlreadyRefreshed = false;
        int attempt = 1;

        while (true) {
            String token = tokens.getToken();
            request.getHeaders().setBearerAuth(token);

            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);   // body is a byte[]: safe to resend
            } catch (IOException e) {
                if (!retryPolicy.shouldRetryAfterIoError(request, attempt)) {
                    throw e;
                }
                Duration wait = retryPolicy.backoff(attempt, null);
                log.warn("Amadeus {} {} network error (attempt {}), retrying in {} ms: {}",
                        request.getMethod(), request.getURI().getPath(), attempt, wait.toMillis(), e.toString());
                sleep(wait);
                attempt++;
                continue;
            }

            int status = response.getStatusCode().value();

            if (status == 401 && !tokenAlreadyRefreshed) {
                response.close();
                tokens.invalidate(token);
                tokenAlreadyRefreshed = true;   // the refresh retry doesn't count as an attempt
                log.info("Amadeus returned 401, refreshing token and retrying once");
                continue;
            }

            if (retryPolicy.shouldRetry(request, status, attempt)) {
                Duration wait = retryPolicy.backoff(attempt, response.getHeaders().getFirst("Retry-After"));
                response.close();
                log.warn("Amadeus {} {} returned {} (attempt {}), retrying in {} ms",
                        request.getMethod(), request.getURI().getPath(), status, attempt, wait.toMillis());
                sleep(wait);
                attempt++;
                continue;
            }

            return response;
        }
    }

    /** Cheap on virtual threads: a sleeping virtual thread doesn't hold an OS thread. */
    private static void sleep(Duration wait) throws IOException {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to retry Amadeus call");
        }
    }
}
