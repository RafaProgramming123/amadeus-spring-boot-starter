package io.github.rafaprogramming123.amadeus;

import io.github.rafaprogramming123.amadeus.autoconfigure.AmadeusAutoConfiguration;
import io.github.rafaprogramming123.amadeus.dto.search.FlightOffersSearchRequest;
import io.github.rafaprogramming123.amadeus.dto.search.FlightOffersSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDate;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real call against Amadeus: fetches a token and runs one search.
 * Skipped unless credentials are in the environment, so CI never hits the network.
 *
 *   AMADEUS_CLIENT_ID / AMADEUS_CLIENT_SECRET   required
 *   AMADEUS_BASE_URL                            optional, defaults to the Enterprise test host
 */
@EnabledIfEnvironmentVariable(named = "AMADEUS_CLIENT_ID", matches = ".+")
class AmadeusLiveTest {

    @Test
    void searchesRealFlightOffers() {
        String baseUrl = Objects.requireNonNullElse(System.getenv("AMADEUS_BASE_URL"),
                "https://test.travel.api.amadeus.com");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AmadeusAutoConfiguration.class))
                .withPropertyValues(
                        "amadeus.client-id=" + System.getenv("AMADEUS_CLIENT_ID"),
                        "amadeus.client-secret=" + System.getenv("AMADEUS_CLIENT_SECRET"),
                        "amadeus.base-url=" + baseUrl)
                .run(ctx -> {
                    Amadeus amadeus = ctx.getBean(Amadeus.class);

                    FlightOffersSearchResponse response = amadeus.shopping.flightOffersSearch.post(
                            FlightOffersSearchRequest.oneWay("MAD", "BCN", LocalDate.now().plusDays(45), 1, 5, "EUR"));

                    System.out.printf("Amadeus returned %d offers, cheapest: %s%n",
                            response.offers().size(),
                            response.cheapest().map(o -> o.price().grandTotal() + " " + o.price().currency()).orElse("-"));
                    assertThat(response.offers()).isNotEmpty();
                    assertThat(response.offers().getFirst().raw()).isNotNull();
                });
    }
}
