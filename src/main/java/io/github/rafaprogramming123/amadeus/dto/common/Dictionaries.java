package io.github.rafaprogramming123.amadeus.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Lookup tables Amadeus sends alongside offers: codes → names. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dictionaries(
        Map<String, Location> locations,   // "SKP" -> {cityCode, countryCode}
        Map<String, String> aircraft,      // "320" -> "AIRBUS A320"
        Map<String, String> currencies,    // "EUR" -> "EURO"
        Map<String, String> carriers       // "OS"  -> "AUSTRIAN AIRLINES"
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String cityCode, String countryCode) {}

    public String carrierName(String code) {
        return carriers == null ? code : carriers.getOrDefault(code, code);
    }
}
