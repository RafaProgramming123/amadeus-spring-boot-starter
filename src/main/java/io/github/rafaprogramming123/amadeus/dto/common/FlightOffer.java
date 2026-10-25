package io.github.rafaprogramming123.amadeus.dto.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A flight offer as returned by Flight Offers Search and accepted by Flight Offers Price.
 *
 * Round-trip to pricing/booking:
 *  - Offers obtained through the {@code Amadeus} facade carry {@code raw}: the exact JSON
 *    Amadeus sent. That is what gets sent back, so fields not modelled here are never lost.
 *  - Offers without {@code raw} (e.g. posted to your API by a front end) are serialized from
 *    the typed fields. For that fallback: amounts/durations stay String exactly as sent,
 *    date-times keep seconds, and null fields are omitted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlightOffer(
        String type,
        String id,
        String source,
        Boolean instantTicketingRequired,
        Boolean nonHomogeneous,
        Boolean oneWay,
        Boolean isUpsellOffer,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate lastTicketingDate,
        String lastTicketingDateTime,
        Integer numberOfBookableSeats,
        List<Itinerary> itineraries,
        Price price,
        PricingOptions pricingOptions,
        List<String> validatingAirlineCodes,
        List<TravelerPricing> travelerPricings,
        @JsonIgnore JsonNode raw   // original JSON from Amadeus; never serialized, see AmadeusJson
) {

    /** Copy of this offer that remembers the exact JSON Amadeus sent. */
    public FlightOffer withRaw(JsonNode original) {
        return new FlightOffer(type, id, source, instantTicketingRequired, nonHomogeneous, oneWay,
                isUpsellOffer, lastTicketingDate, lastTicketingDateTime, numberOfBookableSeats,
                itineraries, price, pricingOptions, validatingAirlineCodes, travelerPricings, original);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Itinerary(String duration, List<Segment> segments) {
        public Duration durationValue() { return duration == null ? null : Duration.parse(duration); }
        public int stops() { return segments == null ? 0 : segments.size() - 1; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Segment(
            FlightEndpoint departure,
            FlightEndpoint arrival,
            String carrierCode,
            String number,
            Aircraft aircraft,
            Operating operating,
            String duration,
            String id,
            Integer numberOfStops,
            Boolean blacklistedInEU,
            List<Stop> stops,
            List<Co2Emission> co2Emissions
    ) {
        public String flightNumber() { return carrierCode + number; }   // e.g. "OS774"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FlightEndpoint(
            String iataCode,
            String terminal,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime at
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Stop(
            String iataCode,
            String duration,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime arrivalAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime departureAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aircraft(String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operating(String carrierCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Co2Emission(Integer weight, String weightUnit, String cabin) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Price(
            String currency,
            String total,
            String base,
            List<Fee> fees,
            String grandTotal,
            List<Fee> additionalServices,
            List<Tax> taxes,
            String refundableTaxes
    ) {
        public BigDecimal totalAmount() { return total == null ? null : new BigDecimal(total); }
        public BigDecimal grandTotalAmount() { return grandTotal == null ? null : new BigDecimal(grandTotal); }
        public BigDecimal baseAmount() { return base == null ? null : new BigDecimal(base); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fee(String amount, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tax(String amount, String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PricingOptions(
            List<String> fareType,
            Boolean includedCheckedBagsOnly,
            Boolean refundableFare,
            Boolean noRestrictionFare,
            Boolean noPenaltyFare
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TravelerPricing(
            String travelerId,
            String fareOption,
            String travelerType,
            String associatedAdultId,
            Price price,
            List<FareDetailsBySegment> fareDetailsBySegment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FareDetailsBySegment(
            String segmentId,
            String cabin,
            String fareBasis,
            String brandedFare,
            String brandedFareLabel,
            @JsonProperty("class") String bookingClass,   // "class" is a Java keyword
            Boolean isAllotment,
            BaggageAllowance includedCheckedBags,
            BaggageAllowance includedCabinBags,
            List<Amenity> amenities
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BaggageAllowance(Integer quantity, Integer weight, String weightUnit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Amenity(String description, Boolean isChargeable, String amenityType, AmenityProvider amenityProvider) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmenityProvider(String name) {}
}
