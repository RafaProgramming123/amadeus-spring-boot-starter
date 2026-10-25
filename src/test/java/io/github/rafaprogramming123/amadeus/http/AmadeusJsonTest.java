package io.github.rafaprogramming123.amadeus.http;

import io.github.rafaprogramming123.amadeus.dto.common.FlightOffer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Typed mapping + lossless round-trip of offers back to pricing/booking. */
class AmadeusJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AmadeusJson json = new AmadeusJson(mapper);

    private static final String OFFERS = """
            [{
              "type": "flight-offer", "id": "1", "source": "GDS", "oneWay": false,
              "numberOfBookableSeats": 4,
              "futureField": {"x": 1},
              "itineraries": [{
                "duration": "PT2H10M",
                "segments": [{
                  "departure": {"iataCode": "SKP", "at": "2026-11-20T06:10:00"},
                  "arrival":   {"iataCode": "VIE", "terminal": "3", "at": "2026-11-20T08:20:00"},
                  "carrierCode": "OS", "number": "774", "id": "1",
                  "newSegmentField": true
                }]
              }],
              "price": {"currency": "EUR", "total": "123.40", "base": "80.00", "grandTotal": "123.40"},
              "validatingAirlineCodes": ["OS"],
              "travelerPricings": [{
                "travelerId": "1", "fareOption": "STANDARD", "travelerType": "ADULT",
                "price": {"currency": "EUR", "total": "123.40", "base": "80.00"},
                "fareDetailsBySegment": [{"segmentId": "1", "cabin": "ECONOMY", "class": "K",
                  "includedCheckedBags": {"quantity": 1}}]
              }]
            }]
            """;

    private List<FlightOffer> parse() {
        return json.offersWithRaw(mapper.readTree(OFFERS));
    }

    @Test
    void mapsIntoTypedFields() {
        FlightOffer offer = parse().getFirst();
        var segment = offer.itineraries().getFirst().segments().getFirst();

        assertThat(offer.price().grandTotalAmount()).isEqualByComparingTo(new BigDecimal("123.40"));
        assertThat(segment.flightNumber()).isEqualTo("OS774");
        assertThat(segment.departure().at()).isEqualTo(LocalDateTime.of(2026, 11, 20, 6, 10));
        assertThat(offer.itineraries().getFirst().durationValue()).isEqualTo(Duration.ofMinutes(130));
        assertThat(offer.itineraries().getFirst().stops()).isZero();
        assertThat(offer.travelerPricings().getFirst().fareDetailsBySegment().getFirst().bookingClass())
                .isEqualTo("K");
    }

    @Test
    void sendsBackOriginalJsonIncludingFieldsWeDoNotModel() {
        JsonNode wire = json.toWire(parse().getFirst());

        assertThat(wire.has("futureField")).isTrue();
        assertThat(wire.get("itineraries").get(0).get("segments").get(0).has("newSegmentField")).isTrue();
    }

    @Test
    void typedFallbackKeepsAmadeusFormats() {
        FlightOffer withoutRaw = parse().getFirst().withRaw(null);   // e.g. posted by a front end
        JsonNode wire = json.toWire(withoutRaw);
        JsonNode segment = wire.get("itineraries").get(0).get("segments").get(0);
        JsonNode fare = wire.get("travelerPricings").get(0).get("fareDetailsBySegment").get(0);

        assertThat(wire.has("raw")).isFalse();                                   // internal field never leaks
        assertThat(wire.get("price").get("total").toString()).isEqualTo("\"123.40\"");   // string, not 123.4
        assertThat(segment.get("departure").get("at").toString()).isEqualTo("\"2026-11-20T06:10:00\"");
        assertThat(fare.has("class")).isTrue();                                  // not "bookingClass"
        assertThat(segment.get("departure").has("terminal")).isFalse();          // nulls omitted
    }

    @Test
    void rawIsNotExposedWhenOffersAreReturnedFromYourApi() throws Exception {
        String out = mapper.writeValueAsString(parse().getFirst());
        assertThat(out).doesNotContain("\"raw\"").doesNotContain("futureField");
    }
}
