package io.github.rafaprogramming123.amadeus;

import io.github.rafaprogramming123.amadeus.api.FlightOffersApi;
import io.github.rafaprogramming123.amadeus.dto.search.FlightOffersSearchRequest;
import io.github.rafaprogramming123.amadeus.dto.search.FlightOffersSearchResponse;
import io.github.rafaprogramming123.amadeus.http.AmadeusJson;
import tools.jackson.databind.JsonNode;

/**
 * Single entry point, SDK-style. Inject it anywhere and call:
 *
 *   amadeus.shopping.flightOffersSearch.post(request)        → typed search results
 *
 * Every method returns typed DTOs. Offers keep the exact JSON Amadeus sent ({@code raw}),
 * so no field is ever lost on the way.
 * Errors surface as AmadeusException; auth and retries happen underneath.
 */
public final class Amadeus {

    public final Shopping shopping;

    public Amadeus(FlightOffersApi offersApi, AmadeusJson json) {
        this.shopping = new Shopping(offersApi, json);
    }

    // ---------------- /shopping ----------------

    public static final class Shopping {
        public final FlightOffersSearch flightOffersSearch;   // /v2/shopping/flight-offers

        private Shopping(FlightOffersApi offersApi, AmadeusJson json) {
            this.flightOffersSearch = new FlightOffersSearch(offersApi, json);
        }
    }

    public static final class FlightOffersSearch {
        private final FlightOffersApi api;
        private final AmadeusJson json;

        private FlightOffersSearch(FlightOffersApi api, AmadeusJson json) {
            this.api = api;
            this.json = json;
        }

        /** POST /v2/shopping/flight-offers */
        public FlightOffersSearchResponse post(FlightOffersSearchRequest request) {
            JsonNode root = api.search(request, "GET");
            return json.read(root, FlightOffersSearchResponse.class)
                    .withOffers(json.offersWithRaw(root.get("data")));
        }
    }
}
