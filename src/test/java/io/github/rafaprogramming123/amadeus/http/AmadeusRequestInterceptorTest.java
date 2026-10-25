package io.github.rafaprogramming123.amadeus.http;

import io.github.rafaprogramming123.amadeus.auth.AmadeusTokenManager;
import io.github.rafaprogramming123.amadeus.auth.TokenResponse;
import io.github.rafaprogramming123.amadeus.error.AmadeusErrorHandler;
import io.github.rafaprogramming123.amadeus.error.AmadeusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Exercises the full stack: interceptor (auth + retry) + error handler, against a mock server. */
class AmadeusRequestInterceptorTest {

    private static final String URL = "https://amadeus.test/v2/shopping/flight-offers";

    private MockRestServiceServer server;
    private RestClient client;
    private AtomicInteger tokenFetches;

    @BeforeEach
    void setUp() {
        tokenFetches = new AtomicInteger();
        var tokens = new AmadeusTokenManager(
                () -> new TokenResponse("tok-" + tokenFetches.incrementAndGet(), "Bearer", 1799),
                Duration.ofSeconds(60), Clock.systemUTC());
        var retry = new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(5));

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder
                .baseUrl("https://amadeus.test")
                .requestInterceptor(new AmadeusRequestInterceptor(tokens, retry))
                .defaultStatusHandler(HttpStatusCode::isError, new AmadeusErrorHandler(JsonMapper.builder().build()))
                .build();
    }

    private String searchPost() {
        return client.post().uri("/v2/shopping/flight-offers")
                .header(RetryPolicy.METHOD_OVERRIDE, "GET")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
    }

    private String bookingPost() {
        return client.post().uri("/v1/booking/flight-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
    }

    @Test
    void attachesBearerToken() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(searchPost()).contains("data");
        server.verify();
    }

    @Test
    void on401RefreshesTokenAndRetriesOnce() {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer tok-2"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(searchPost()).contains("data");
        assertThat(tokenFetches).hasValue(2);
        server.verify();
    }

    @Test
    void secondConsecutive401BecomesAmadeusException() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(this::searchPost)
                .isInstanceOfSatisfying(AmadeusException.class, ex -> assertThat(ex.isAuthError()).isTrue());
        server.verify();
    }

    @Test
    void readOnlySearchIsRetriedOn503() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(searchPost()).contains("data");
        server.verify();
    }

    @Test
    void retriesStopAtMaxAttempts() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        }

        assertThatThrownBy(this::searchPost)
                .isInstanceOfSatisfying(AmadeusException.class, ex -> assertThat(ex.status()).isEqualTo(502));
        server.verify();
    }

    @Test
    void bookingIsNeverRetriedOn5xx() {
        server.expect(requestTo("https://amadeus.test/v1/booking/flight-orders"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(this::bookingPost)
                .isInstanceOfSatisfying(AmadeusException.class, ex -> assertThat(ex.status()).isEqualTo(503));
        server.verify();   // fails if a second request was sent
    }

    @Test
    void bookingIsRetriedOn429BecauseNothingWasProcessed() {
        server.expect(requestTo("https://amadeus.test/v1/booking/flight-orders"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        server.expect(requestTo("https://amadeus.test/v1/booking/flight-orders"))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThat(bookingPost()).contains("data");
        server.verify();
    }

    @Test
    void amadeusErrorBodyIsParsedIntoIssues() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"errors":[{"status":400,"code":477,"title":"INVALID FORMAT",
                          "detail":"departureDate must be in the future",
                          "source":{"pointer":"/originDestinations[0]/departureDateTimeRange/date"}}]}
                        """));

        assertThatThrownBy(this::searchPost)
                .isInstanceOfSatisfying(AmadeusException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(400);
                    assertThat(ex.hasCode(477)).isTrue();
                    assertThat(ex.issues().getFirst().detail()).contains("future");
                    assertThat(ex.getMessage()).contains("INVALID FORMAT");
                });
        server.verify();
    }

    @Test
    void nonJsonErrorBodyStillProducesAmadeusException() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_HTML)
                .body("<html>Bad Request</html>"));

        assertThatThrownBy(this::searchPost)
                .isInstanceOfSatisfying(AmadeusException.class, ex -> {
                    assertThat(ex.issues()).isEmpty();
                    assertThat(ex.rawBody()).contains("Bad Request");
                });
    }
}
