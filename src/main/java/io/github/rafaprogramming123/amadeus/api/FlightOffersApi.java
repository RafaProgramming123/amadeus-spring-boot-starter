package io.github.rafaprogramming123.amadeus.api;

import io.github.rafaprogramming123.amadeus.dto.search.FlightOffersSearchRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.JsonNode;

/**
 * Low-level HTTP binding for Flight Offers Search. Use it through the {@code Amadeus}
 * facade, which turns the JsonNode into typed DTOs while keeping each offer's original JSON.
 */
@HttpExchange(url = "/v2/shopping", contentType = "application/json", accept = "application/vnd.amadeus+json")
public interface FlightOffersApi {

    /** POST /v2/shopping/flight-offers; Amadeus expects the method-override header here. */
    @PostExchange("/flight-offers")
    JsonNode search(@RequestBody FlightOffersSearchRequest request,
                    @RequestHeader("X-HTTP-Method-Override") String methodOverride);
}
