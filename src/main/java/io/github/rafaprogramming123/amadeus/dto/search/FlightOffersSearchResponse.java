package io.github.rafaprogramming123.amadeus.dto.search;

import io.github.rafaprogramming123.amadeus.dto.common.Dictionaries;
import io.github.rafaprogramming123.amadeus.dto.common.FlightOffer;
import io.github.rafaprogramming123.amadeus.dto.common.Issue;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Response of POST /v2/shopping/flight-offers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightOffersSearchResponse(
        Meta meta,
        List<FlightOffer> data,
        Dictionaries dictionaries,
        List<Issue> warnings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Integer count, Links links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String self) {}

    public FlightOffersSearchResponse withOffers(List<FlightOffer> offers) {
        return new FlightOffersSearchResponse(meta, offers, dictionaries, warnings);
    }

    public List<FlightOffer> offers() {
        return data == null ? List.of() : data;
    }

    public Optional<FlightOffer> cheapest() {
        return offers().stream()
                .filter(o -> o.price() != null && o.price().grandTotal() != null)
                .min(Comparator.comparing(o -> o.price().grandTotalAmount()));
    }
}
