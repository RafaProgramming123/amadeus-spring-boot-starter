package io.github.rafaprogramming123.amadeus.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Body for POST /v2/shopping/flight-offers. Null fields are not sent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlightOffersSearchRequest(
        String currencyCode,
        List<OriginDestination> originDestinations,
        List<Traveler> travelers,
        List<String> sources,
        SearchCriteria searchCriteria
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OriginDestination(
            String id,
            String originLocationCode,
            String destinationLocationCode,
            DateTimeRange departureDateTimeRange,
            DateTimeRange arrivalDateTimeRange
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DateTimeRange(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            String time,        // "10:00:00"
            String dateWindow,  // e.g. "I3D" = ±3 days
            String timeWindow   // e.g. "2H"
    ) {
        public static DateTimeRange on(LocalDate date) {
            return new DateTimeRange(date, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Traveler(String id, TravelerType travelerType, String associatedAdultId) {}

    public enum TravelerType { ADULT, CHILD, SENIOR, YOUNG, HELD_INFANT, SEATED_INFANT, STUDENT }

    public enum Cabin { ECONOMY, PREMIUM_ECONOMY, BUSINESS, FIRST }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchCriteria(
            Integer maxFlightOffers,
            Boolean excludeAllotments,
            Boolean addOneWayOffers,
            FlightFilters flightFilters,
            PricingOptions pricingOptions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FlightFilters(
            List<CabinRestriction> cabinRestrictions,
            CarrierRestrictions carrierRestrictions,
            ConnectionRestriction connectionRestriction
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CabinRestriction(Cabin cabin, String coverage, List<String> originDestinationIds) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CarrierRestrictions(List<String> includedCarrierCodes, List<String> excludedCarrierCodes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConnectionRestriction(Integer maxNumberOfConnections, Boolean nonStopPreferred) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PricingOptions(Boolean includedCheckedBagsOnly) {}

    // ---------- convenience factories ----------

    public static FlightOffersSearchRequest oneWay(String from, String to, LocalDate date,
                                                   int adults, int maxOffers, String currency) {
        return new FlightOffersSearchRequest(
                currency,
                List.of(new OriginDestination("1", from, to, DateTimeRange.on(date), null)),
                adults(adults),
                List.of("GDS"),
                new SearchCriteria(maxOffers, null, null, null, null));
    }

    public static FlightOffersSearchRequest roundTrip(String from, String to, LocalDate out, LocalDate back,
                                                      int adults, int maxOffers, String currency) {
        return new FlightOffersSearchRequest(
                currency,
                List.of(new OriginDestination("1", from, to, DateTimeRange.on(out), null),
                        new OriginDestination("2", to, from, DateTimeRange.on(back), null)),
                adults(adults),
                List.of("GDS"),
                new SearchCriteria(maxOffers, null, null, null, null));
    }

    private static List<Traveler> adults(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Traveler(String.valueOf(i), TravelerType.ADULT, null))
                .toList();
    }
}
